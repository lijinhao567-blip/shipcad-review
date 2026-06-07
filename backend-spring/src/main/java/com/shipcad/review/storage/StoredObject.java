package com.shipcad.review.storage;

import java.nio.file.Path;

public record StoredObject(
        String key,
        String storageMode,
        Path localPath,
        long size,
        String contentType
) {
}
