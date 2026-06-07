package com.shipcad.review.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalObjectStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesBytesUnderNormalizedKey() throws Exception {
        LocalObjectStorageService storage = new LocalObjectStorageService(tempDir.toString());

        StoredObject stored = storage.storeBytes("rendered/version-1/render.png", new byte[]{1, 2, 3}, "image/png");

        assertThat(stored.storageMode()).isEqualTo("local");
        assertThat(stored.key()).isEqualTo("rendered/version-1/render.png");
        assertThat(Files.readAllBytes(stored.localPath())).containsExactly(1, 2, 3);
        assertThat(storage.exists(stored.key())).isTrue();
        assertThat(storage.resolveLocalPath(stored.key())).isEqualTo(stored.localPath());
    }

    @Test
    void storesMultipartAndRejectsPathTraversal() throws Exception {
        LocalObjectStorageService storage = new LocalObjectStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.dxf",
                "application/dxf",
                "0\nSECTION\n0\nEOF\n".getBytes(StandardCharsets.UTF_8)
        );

        StoredObject stored = storage.storeMultipart("uploads/drawing-1/version-1_sample.dxf", file, "application/dxf");

        assertThat(Files.readString(stored.localPath())).contains("SECTION");
        assertThatThrownBy(() -> storage.storeBytes("../escape.dxf", new byte[]{1}, "application/dxf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key非法");
    }
}
