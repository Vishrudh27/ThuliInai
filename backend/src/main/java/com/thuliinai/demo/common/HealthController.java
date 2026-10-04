package com.thuliinai.demo.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", "UP", "db", "UP");
        } catch (Exception e) {
            return Map.of("status", "UP", "db", "DOWN", "error", e.getMessage());
        }
    }

    @GetMapping("/hello")
    public String hello() {
        return "ThuliInai Backend is running";
    }

    @GetMapping("/hellodb")
    public String hellodb() {
        String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        return "Connected to database: " + dbName;
    }
}
