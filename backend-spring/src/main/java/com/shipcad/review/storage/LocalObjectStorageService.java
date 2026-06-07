package com.shipcad.review.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "shipcad.object-storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {
    private final Path root;

    public LocalObjectStorageService(@Value("${shipcad.object-storage.local.root:${shipcad.storage-root}}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String mode() {
        return "local";
    }

    @Override
    public StoredObject storeMultipart(String key, MultipartFile file, String contentType) throws IOException {
        String normalizedKey = StorageKeys.normalize(key);
        Path target = pathFor(normalizedKey);
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return new StoredObject(normalizedKey, mode(), target, Files.size(target), contentType);
    }

    @Override
    public StoredObject storeBytes(String key, byte[] content, String contentType) throws IOException {
        String normalizedKey = StorageKeys.normalize(key);
        Path target = pathFor(normalizedKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content == null ? new byte[0] : content);
        return new StoredObject(normalizedKey, mode(), target, Files.size(target), contentType);
    }

    @Override
    public boolean exists(String key) {
        try {
            Path target = pathFor(StorageKeys.normalize(key));
            return Files.isRegularFile(target) && Files.size(target) > 0;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public Path resolveLocalPath(String key) {
        Path target = pathFor(StorageKeys.normalize(key));
        if (!Files.isRegularFile(target) || !Files.isReadable(target)) {
            throw new IllegalArgumentException("对象文件不可读取: " + key);
        }
        return target;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode());
        result.put("root", root.toString());
        try {
            Files.createDirectories(root);
            result.put("status", Files.isDirectory(root) && Files.isWritable(root) ? "ok" : "down");
        } catch (IOException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        }
        return result;
    }

    private Path pathFor(String key) {
        Path target = root.resolve(key).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("对象文件路径非法");
        }
        return target;
    }
}
