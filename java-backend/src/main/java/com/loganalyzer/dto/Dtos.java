package com.loganalyzer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// ─── Login ────────────────────────────────────────────────────────────────────
public class Dtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String message;
        private String username;
        private String role;
        private boolean success;
    }

    // ─── Log Ingestion ──────────────────────────────────────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogIngestRequest {
        private String level;
        private String message;
        private String source;
        private String threadName;
        private String requestId;
        private LocalDateTime timestamp;
    }

    // ─── Prediction (Java → Python) ────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionRequest {
        private Long logId;
        private String level;
        private String message;
        private String source;
        private LocalDateTime timestamp;
        private int errorCount;
        private double responseTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionResponse {
        private Long logId;
        private boolean anomaly;
        private double score;
        private String reason;
    }

    // ─── Dashboard Stats ───────────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStats {
        private long totalLogs;
        private long totalAnomalies;
        private long logsLast24h;
        private Map<String, Long> logsByLevel;
        private List<RecentAnomaly> recentAnomalies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentAnomaly {
        private Long id;
        private LocalDateTime timestamp;
        private String level;
        private String message;
        private double score;
    }

    // ─── API Response Wrapper ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(T data) {
            return ApiResponse.<T>builder().success(true).data(data).message("OK").build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder().success(false).message(message).build();
        }
    }
}
