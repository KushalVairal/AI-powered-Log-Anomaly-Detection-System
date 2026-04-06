package com.loganalyzer.service;

import com.loganalyzer.dto.Dtos;
import com.loganalyzer.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 2 – Log Preprocessing
 * Cleans raw log lines and extracts structured fields:
 *   timestamp | level | source | message
 */
@Service
@Slf4j
public class LogPreprocessingService {

    // Matches standard Spring Boot log format:
    // 2024-01-15 10:23:45.123  INFO 1234 --- [main] c.l.SomeClass : Some message
    private static final Pattern SPRING_LOG_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)" +
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)" +
        "\\s+\\d+" +
        "\\s+---\\s+\\[[^]]+]" +
        "\\s+([\\w.$]+)\\s*:" +
        "\\s+(.+)$"
    );

    // Generic syslog-style pattern
    private static final Pattern GENERIC_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)" +
        "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+(.+)$"
    );

    /**
     * Parse a raw log line into a LogEntry.
     * Returns null if the line cannot be parsed.
     */
    public LogEntry parseRawLog(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return null;

        // Try Spring Boot pattern first
        Matcher m = SPRING_LOG_PATTERN.matcher(rawLine.trim());
        if (m.matches()) {
            return LogEntry.builder()
                    .timestamp(parseTimestamp(m.group(1)))
                    .level(m.group(2).toUpperCase())
                    .source(m.group(3))
                    .message(cleanMessage(m.group(4)))
                    .rawLog(rawLine)
                    .build();
        }

        // Fallback to generic pattern
        Matcher gm = GENERIC_PATTERN.matcher(rawLine.trim());
        if (gm.matches()) {
            return LogEntry.builder()
                    .timestamp(parseTimestamp(gm.group(1)))
                    .level(gm.group(2).toUpperCase())
                    .message(cleanMessage(gm.group(3)))
                    .rawLog(rawLine)
                    .build();
        }

        // Unrecognised format — store as-is with INFO level
        log.debug("Could not parse log line: {}", rawLine);
        return LogEntry.builder()
                .timestamp(LocalDateTime.now())
                .level("INFO")
                .message(cleanMessage(rawLine))
                .rawLog(rawLine)
                .build();
    }

    /**
     * Convert an ingest DTO to a LogEntry.
     */
    public LogEntry fromIngestRequest(Dtos.LogIngestRequest req) {
        return LogEntry.builder()
                .timestamp(req.getTimestamp() != null ? req.getTimestamp() : LocalDateTime.now())
                .level(normalizeLevel(req.getLevel()))
                .message(cleanMessage(req.getMessage()))
                .source(req.getSource())
                .threadName(req.getThreadName())
                .requestId(req.getRequestId())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private LocalDateTime parseTimestamp(String raw) {
        try {
            // Normalise separator: 'T' or space
            return LocalDateTime.parse(raw.replace(" ", "T").replaceAll("\\.\\d+$", ""));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String cleanMessage(String msg) {
        if (msg == null) return "";
        return msg
            .replaceAll("\\s+", " ")          // collapse whitespace
            .replaceAll("[^\\x20-\\x7E]", "")  // strip non-printable chars
            .trim();
    }

    private String normalizeLevel(String level) {
        if (level == null) return "INFO";
        return switch (level.toUpperCase()) {
            case "WARNING" -> "WARN";
            case "SEVERE", "CRITICAL", "FATAL" -> "ERROR";
            case "TRACE", "VERBOSE" -> "DEBUG";
            default -> level.toUpperCase();
        };
    }
}
