package com.loganalyzer.service;

import com.loganalyzer.dto.Dtos;
import com.loganalyzer.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 4 – Java ↔ Python Integration
 * Sends a log entry to the Python Flask ML service and gets back
 * an anomaly prediction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PythonMLClientService {

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Calls POST /predict on the Python ML service.
     *
     * @param entry the log entry to classify
     * @return prediction DTO (anomaly flag + score)
     */
    public Dtos.PredictionResponse predict(LogEntry entry) {
        Dtos.PredictionRequest req = buildRequest(entry);

        try {
            String url = mlServiceUrl + "/predict";
            ResponseEntity<Dtos.PredictionResponse> response =
                    restTemplate.postForEntity(url, req, Dtos.PredictionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("ML prediction for log {}: anomaly={}, score={}",
                        entry.getId(), response.getBody().isAnomaly(), response.getBody().getScore());
                return response.getBody();
            }
        } catch (RestClientException ex) {
            log.warn("ML service unreachable — falling back to rule-based detection: {}", ex.getMessage());
        }

        // Fallback: simple rule-based detection when Python service is down
        return ruleBasedFallback(entry);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Dtos.PredictionRequest buildRequest(LogEntry entry) {
        return Dtos.PredictionRequest.builder()
                .logId(entry.getId())
                .level(entry.getLevel())
                .message(entry.getMessage())
                .source(entry.getSource())
                .timestamp(entry.getTimestamp())
                .build();
    }

    /**
     * Fallback rule-based anomaly detection (used when ML service is offline).
     * Flags ERROR/FATAL logs and messages containing exception keywords.
     */
    private Dtos.PredictionResponse ruleBasedFallback(LogEntry entry) {
        boolean isAnomaly = false;
        double score = 0.0;
        String reason = "normal";

        String level = entry.getLevel() != null ? entry.getLevel() : "";
        String message = entry.getMessage() != null ? entry.getMessage().toLowerCase() : "";

        if ("ERROR".equals(level) || "FATAL".equals(level)) {
            isAnomaly = true;
            score = 0.85;
            reason = "ERROR/FATAL log level";
        } else if (message.contains("exception") || message.contains("stacktrace")
                || message.contains("outofmemory") || message.contains("timeout")) {
            isAnomaly = true;
            score = 0.75;
            reason = "exception/error keyword in message";
        } else if ("WARN".equals(level)) {
            score = 0.35;
            reason = "warning level";
        }

        return new Dtos.PredictionResponse(entry.getId(), isAnomaly, score, reason);
    }
}
