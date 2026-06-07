package com.shipcad.review.storage;

import java.nio.file.Path;
import java.util.Locale;

final class StorageKeys {
    private StorageKeys() {
    }

    static String normalize(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("对象存储key不能为空");
        }
        String normalized = key.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path path = Path.of(normalized).normalize();
        String value = path.toString().replace('\\', '/');
        if (value.isBlank() || value.equals(".") || value.startsWith("../") || value.equals("..") || value.contains("/../")) {
            throw new IllegalArgumentException("对象存储key非法");
        }
        return value;
    }

    static String safeFileName(String originalName, String fallback) {
        String value = originalName == null || originalName.isBlank() ? fallback : originalName.trim();
        value = value.replace('\\', '/');
        int lastSeparator = value.lastIndexOf('/');
        if (lastSeparator >= 0) {
            value = value.substring(lastSeparator + 1);
        }
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            return fallback;
        }
        return value;
    }

    static String contentTypeForFileName(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dxf")) {
            return "application/dxf";
        }
        if (lower.endsWith(".dwg")) {
            return "application/octet-stream";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return fallback;
    }
}
