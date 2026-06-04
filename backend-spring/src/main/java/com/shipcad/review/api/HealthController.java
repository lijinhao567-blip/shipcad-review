package com.shipcad.review.api;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("time", Instant.now().toString());
        result.put("database", databaseHealth());
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
}
