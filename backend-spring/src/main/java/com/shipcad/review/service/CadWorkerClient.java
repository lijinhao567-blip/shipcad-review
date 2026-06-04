package com.shipcad.review.service;

import com.shipcad.review.dto.ApiDtos.WorkerParseResponse;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class CadWorkerClient {
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

    public byte[] render(Path file, int width, int height) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        return restClient.post()
                .uri(uri -> uri.path("/render").queryParam("width", width).queryParam("height", height).build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(byte[].class);
    }
}
