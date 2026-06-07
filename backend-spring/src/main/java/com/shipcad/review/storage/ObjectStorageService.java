package com.shipcad.review.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {
    String mode();

    StoredObject storeMultipart(String key, MultipartFile file, String contentType) throws IOException;

    StoredObject storeBytes(String key, byte[] content, String contentType) throws IOException;

    boolean exists(String key);

    Path resolveLocalPath(String key) throws IOException;

    Map<String, Object> health();
}
