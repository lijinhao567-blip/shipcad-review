package com.shipcad.review.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.service.ReviewTaskQueue;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private static final Duration WORKER_TIMEOUT = Duration.ofSeconds(2);

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final ReviewTaskQueue reviewTaskQueue;
    private final String cadWorkerUrl;
    private final String visionWorkerUrl;
    private final String ocrWorkerUrl;

    public HealthController(
            DataSource dataSource,
            ObjectMapper mapper,
            ReviewTaskQueue reviewTaskQueue,
            @Value("${shipcad.worker-url}") String cadWorkerUrl,
            @Value("${shipcad.vision-url}") String visionWorkerUrl,
            @Value("${shipcad.ocr-url}") String ocrWorkerUrl
    ) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(WORKER_TIMEOUT).build();
        this.reviewTaskQueue = reviewTaskQueue;
        this.cadWorkerUrl = cadWorkerUrl;
        this.visionWorkerUrl = visionWorkerUrl;
        this.ocrWorkerUrl = ocrWorkerUrl;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", Instant.now().toString());
        Map<String, Object> database = databaseHealth();
        Map<String, Object> workers = new LinkedHashMap<>();
        workers.put("cad", workerHealth("CAD Worker", cadWorkerUrl, true));
        workers.put("vision", workerHealth("Vision Worker", visionWorkerUrl, false));
        workers.put("ocr", workerHealth("OCR Worker", ocrWorkerUrl, false));
        Map<String, Object> queue = reviewTaskQueue.health();
        result.put("status", overallStatus(database, workers, queue));
        result.put("database", database);
        result.put("queue", queue);
        result.put("openapi", Map.of("status", "ok", "url", "/swagger-ui.html"));
        result.put("workers", workers);
        return result;
    }

    private Map<String, Object> databaseHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            result.put("status", connection.isValid(2) ? "ok" : "down");
            result.put("url", connection.getMetaData().getURL());
        } catch (SQLException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        }
        return result;
    }

    private Map<String, Object> workerHealth(String name, String baseUrl, boolean required) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("baseUrl", baseUrl);
        result.put("required", required);
        try {
            HttpResponse<String> health = getJson(baseUrl + "/health");
            result.put("status", statusOf(health));
            result.put("statusCode", health.statusCode());
            result.put("health", parseBody(health.body()));
            if (health.statusCode() >= 200 && health.statusCode() < 400) {
                HttpResponse<String> capabilities = getJson(baseUrl + "/capabilities");
                result.put("capabilitiesStatusCode", capabilities.statusCode());
                result.put("capabilities", parseBody(capabilities.body()));
            }
        } catch (IOException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result.put("status", "down");
            result.put("error", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        }
        return result;
    }

    private HttpResponse<String> getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(WORKER_TIMEOUT)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String statusOf(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 400 ? "ok" : "down";
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(body, Object.class);
        } catch (JsonProcessingException exception) {
            return body;
        }
    }

    private String overallStatus(Map<String, Object> database, Map<String, Object> workers, Map<String, Object> queue) {
        boolean databaseOk = "ok".equals(database.get("status"));
        boolean queueOk = "ok".equals(queue.get("status"));
        boolean requiredWorkersOk = true;
        for (Object value : workers.values()) {
            if (value instanceof Map<?, ?> worker
                    && Boolean.TRUE.equals(worker.get("required"))
                    && !"ok".equals(worker.get("status"))) {
                requiredWorkersOk = false;
                break;
            }
        }
        return databaseOk && queueOk && requiredWorkersOk ? "ok" : "degraded";
    }
}
