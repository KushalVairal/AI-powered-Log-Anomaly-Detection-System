package com.loganalyzer.controller;

import com.loganalyzer.dto.Dtos;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.service.LogAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 2–5 – Log APIs
 *
 * POST  /api/logs/ingest        — ingest a structured log entry
 * POST  /api/logs/ingest-raw    — ingest a raw log line (string)
 * GET   /api/logs               — get latest 20 logs
 * GET   /api/logs/anomalies     — get all flagged anomalies
 * GET   /api/logs/stats         — dashboard statistics
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LogController {

    private final LogAnalysisService analysisService;

    // ── Ingestion ──────────────────────────────────────────────────────────────

    @PostMapping("/ingest")
    public ResponseEntity<Dtos.ApiResponse<LogEntry>> ingest(
            @RequestBody Dtos.LogIngestRequest req) {
        LogEntry saved = analysisService.ingestLog(req);
        return ResponseEntity.ok(Dtos.ApiResponse.ok(saved));
    }

    @PostMapping("/ingest-raw")
    public ResponseEntity<Dtos.ApiResponse<LogEntry>> ingestRaw(
            @RequestBody Map<String, String> body) {
        String raw = body.getOrDefault("line", "");
        LogEntry saved = analysisService.ingestRaw(raw);
        if (saved == null) {
            return ResponseEntity.badRequest().body(Dtos.ApiResponse.error("Could not parse log line"));
        }
        return ResponseEntity.ok(Dtos.ApiResponse.ok(saved));
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Dtos.ApiResponse<List<LogEntry>>> latest() {
        return ResponseEntity.ok(Dtos.ApiResponse.ok(analysisService.getLatestLogs()));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<Dtos.ApiResponse<List<LogEntry>>> anomalies() {
        return ResponseEntity.ok(Dtos.ApiResponse.ok(analysisService.getAllAnomalies()));
    }

    @GetMapping("/stats")
    public ResponseEntity<Dtos.ApiResponse<Dtos.DashboardStats>> stats() {
        return ResponseEntity.ok(Dtos.ApiResponse.ok(analysisService.getDashboardStats()));
    }

    // ── Dummy APIs (Phase 2 log generator) ────────────────────────────────────

    @GetMapping("/demo/health")
    public ResponseEntity<Map<String, String>> health() {
        log.info("Health check requested");
        return ResponseEntity.ok(Map.of("status", "UP", "service", "log-analyzer"));
    }

    @GetMapping("/demo/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        log.info("Fetching user list");
        // Intentionally log a warning to generate interesting log data
        log.warn("User list endpoint hit — consider pagination for large datasets");
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        ));
    }

    @GetMapping("/demo/error")
    public ResponseEntity<Map<String, String>> triggerError() {
        log.error("Demo error endpoint triggered — NullPointerException simulation");
        try {
            throw new RuntimeException("Simulated RuntimeException for demo purposes");
        } catch (RuntimeException ex) {
            log.error("Caught exception: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Simulated error", "message", ex.getMessage()));
        }
    }
}
