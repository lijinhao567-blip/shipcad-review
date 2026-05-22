package com.shipcad.review.service;

import java.security.SecureRandom;
import java.time.Instant;

public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String next(String prefix) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(prefix).append("_");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static Instant now() {
        return Instant.now();
    }
}
