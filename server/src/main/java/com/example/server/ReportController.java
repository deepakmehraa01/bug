package com.example.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ReportController {
    private final Map<String, String> reports = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String agentToken;
    private final String viewerToken;

    public ReportController(@Value("${AGENT_TOKEN:}") String agentToken,
                            @Value("${VIEWER_TOKEN:}") String viewerToken) {
        this.agentToken = agentToken;
        this.viewerToken = viewerToken;
    }

    private static void authenticate(String given, String expected) {
        if (expected == null || expected.length() < 24 || given == null
                || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/report")
    public Map<String, String> report(@RequestHeader(value = "X-Agent-Token", required = false) String token,
                                      @RequestBody String body) {
        authenticate(token, agentToken);
        if (body.length() > 200000)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON");
        }
        if (node == null || !node.isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report");
        String id = node.path("deviceId").asText();
        if (!id.matches("[a-zA-Z0-9_-]{1,64}") || !node.path("apps").isArray()
                || !node.path("recordedAt").canConvertToLong()
                || node.path("recordedAt").asLong() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report fields");
        if (node.has("browserHistory") && !node.path("browserHistory").isArray())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid browser history");
        if (node.path("apps").size() > 500 || node.path("browserHistory").size() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many entries");
        if (!reports.containsKey(id) && reports.size() >= 100)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Device limit reached");
        reports.put(id, body);
        return Map.of("status", "received");
    }

    @GetMapping("/api/reports")
    public Map<String, String> reports(
            @RequestHeader(value = "X-Viewer-Token", required = false) String token) {
        authenticate(token, viewerToken);
        return Map.copyOf(reports);
    }

    @DeleteMapping("/api/reports/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReport(@RequestHeader(value = "X-Viewer-Token", required = false) String token,
                             @PathVariable String deviceId) {
        authenticate(token, viewerToken);
        if (!deviceId.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (reports.remove(deviceId) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
