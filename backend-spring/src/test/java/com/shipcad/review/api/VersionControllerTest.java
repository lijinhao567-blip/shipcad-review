package com.shipcad.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ReviewPlatformService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class VersionControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void fileReturnsReadableDxfResource() throws Exception {
        AuthService auth = mock(AuthService.class);
        DrawingVersionRepository versions = mock(DrawingVersionRepository.class);
        ParsedEntityRepository entities = mock(ParsedEntityRepository.class);
        ReviewPlatformService platform = mock(ReviewPlatformService.class);
        VersionController controller = new VersionController(auth, versions, entities, platform);

        Path file = tempDir.resolve("sample.dxf");
        Files.writeString(file, "0\nSECTION\n0\nEOF\n");
        DrawingVersion version = new DrawingVersion();
        version.id = "version-1";
        version.fileName = "sample.dxf";
        version.filePath = file.toString();

        when(auth.requireUser("Bearer token")).thenReturn(new AppUser());
        when(versions.findById("version-1")).thenReturn(Optional.of(version));

        ResponseEntity<Resource> response = controller.file("Bearer token", "version-1");

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/dxf");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exists()).isTrue();
    }
}
