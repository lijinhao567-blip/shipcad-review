package com.shipcad.review.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.dto.ApiDtos.WorkerParseResponse;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class CadWorkerClient {
    private static final String RENDER_METADATA_HEADER = "X-ShipCAD-Render-Metadata";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;

    public CadWorkerClient(@Value("${shipcad.worker-url}") String workerUrl) {
        this.restClient = RestClient.builder().baseUrl(workerUrl).build();
    }

    public WorkerParseResponse parse(Path file) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        return restClient.post()
                .uri("/parse")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(WorkerParseResponse.class);
    }

    public RenderedImage render(Path file, int width, int height) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        ResponseEntity<byte[]> response = restClient.post()
                .uri(uri -> uri.path("/render").queryParam("width", width).queryParam("height", height).build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(byte[].class);
        return new RenderedImage(response.getBody(), decodeMetadata(response.getHeaders().getFirst(RENDER_METADATA_HEADER)));
    }

    private Map<String, Object> decodeMetadata(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("CAD Worker render response is missing coordinate metadata");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return MAPPER.readValue(new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("CAD Worker render coordinate metadata is invalid", exception);
        }
    }

    public record RenderedImage(byte[] content, Map<String, Object> metadata) {
    }
}
