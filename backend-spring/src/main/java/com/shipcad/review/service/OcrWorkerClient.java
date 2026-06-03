package com.shipcad.review.service;

import com.shipcad.review.dto.ApiDtos.OcrResponse;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OcrWorkerClient {
    private final RestClient restClient;

    public OcrWorkerClient(@Value("${shipcad.ocr-url}") String ocrUrl) {
        this.restClient = RestClient.builder().baseUrl(ocrUrl).build();
    }

    public OcrResponse recognize(Path image, double confidence) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(image));
        try {
            return restClient.post()
                    .uri(uri -> uri.path("/ocr").queryParam("confidence", confidence).build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrResponse.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("OCR Worker识别失败: " + exception.getStatusCode() + " " + exception.getResponseBodyAsString(), exception);
        }
    }
}
