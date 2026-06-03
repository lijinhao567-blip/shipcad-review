package com.shipcad.review.service;

import com.shipcad.review.dto.ApiDtos.VisionDetectionResponse;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class VisionWorkerClient {
    private final RestClient restClient;

    public VisionWorkerClient(@Value("${shipcad.vision-url}") String visionUrl) {
        this.restClient = RestClient.builder().baseUrl(visionUrl).build();
    }

    public VisionDetectionResponse detect(Path image, double confidence) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(image));
        try {
            return restClient.post()
                    .uri(uri -> uri.path("/detect").queryParam("confidence", confidence).build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(VisionDetectionResponse.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Vision Worker检测失败: " + exception.getStatusCode() + " " + exception.getResponseBodyAsString(), exception);
        }
    }
}
