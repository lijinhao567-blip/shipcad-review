package com.shipcad.review.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "shipcad.object-storage.mode", havingValue = "s3")
public class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client s3;
    private final String endpoint;
    private final String bucket;
    private final Path cacheRoot;
    private final boolean createBucket;

    public S3ObjectStorageService(
            @Value("${shipcad.object-storage.s3.endpoint:}") String endpoint,
            @Value("${shipcad.object-storage.s3.region:us-east-1}") String region,
            @Value("${shipcad.object-storage.s3.bucket:}") String bucket,
            @Value("${shipcad.object-storage.s3.access-key:}") String accessKey,
            @Value("${shipcad.object-storage.s3.secret-key:}") String secretKey,
            @Value("${shipcad.object-storage.s3.path-style:true}") boolean pathStyle,
            @Value("${shipcad.object-storage.s3.create-bucket:false}") boolean createBucket,
            @Value("${shipcad.object-storage.cache-root:${shipcad.storage-root}/object-cache}") String cacheRoot
    ) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3对象存储bucket不能为空");
        }
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.bucket = bucket;
        this.cacheRoot = Path.of(cacheRoot).toAbsolutePath().normalize();
        this.createBucket = createBucket;

        var builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(credentials(accessKey, secretKey));
        if (!this.endpoint.isBlank()) {
            builder.endpointOverride(URI.create(this.endpoint));
        }
        this.s3 = builder.build();
    }

    @Override
    public String mode() {
        return "s3";
    }

    @Override
    public StoredObject storeMultipart(String key, MultipartFile file, String contentType) throws IOException {
        String normalizedKey = StorageKeys.normalize(key);
        Path local = cachePathFor(normalizedKey);
        Files.createDirectories(local.getParent());
        file.transferTo(local);
        putLocalFile(normalizedKey, local, contentType);
        return new StoredObject(normalizedKey, mode(), local, Files.size(local), contentType);
    }

    @Override
    public StoredObject storeBytes(String key, byte[] content, String contentType) throws IOException {
        String normalizedKey = StorageKeys.normalize(key);
        Path local = cachePathFor(normalizedKey);
        Files.createDirectories(local.getParent());
        Files.write(local, content == null ? new byte[0] : content);
        putLocalFile(normalizedKey, local, contentType);
        return new StoredObject(normalizedKey, mode(), local, Files.size(local), contentType);
    }

    @Override
    public boolean exists(String key) {
        try {
            String normalizedKey = StorageKeys.normalize(key);
            ensureBucket();
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(normalizedKey).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public Path resolveLocalPath(String key) throws IOException {
        String normalizedKey = StorageKeys.normalize(key);
        Path local = cachePathFor(normalizedKey);
        if (Files.isRegularFile(local) && Files.size(local) > 0) {
            return local;
        }
        ensureBucket();
        Files.createDirectories(local.getParent());
        s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(normalizedKey).build(),
                local
        );
        return local;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode());
        result.put("endpoint", endpoint);
        result.put("bucket", bucket);
        result.put("cacheRoot", cacheRoot.toString());
        result.put("createBucket", createBucket);
        try {
            Files.createDirectories(cacheRoot);
            ensureBucket();
            result.put("status", Files.isDirectory(cacheRoot) && Files.isWritable(cacheRoot) ? "ok" : "down");
        } catch (RuntimeException | IOException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        }
        return result;
    }

    private void putLocalFile(String key, Path local, String contentType) throws IOException {
        ensureBucket();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(Files.size(local))
                .build();
        s3.putObject(request, RequestBody.fromFile(local));
    }

    private Path cachePathFor(String key) {
        Path target = cacheRoot.resolve(key).toAbsolutePath().normalize();
        if (!target.startsWith(cacheRoot)) {
            throw new IllegalArgumentException("对象缓存路径非法");
        }
        return target;
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404 && createBucket) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                return;
            }
            throw exception;
        }
    }

    private AwsCredentialsProvider credentials(String accessKey, String secretKey) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
