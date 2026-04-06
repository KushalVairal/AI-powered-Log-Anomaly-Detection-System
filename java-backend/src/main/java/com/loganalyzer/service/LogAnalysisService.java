package com.loganalyzer.service;

import com.loganalyzer.dto.Dtos;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service — orchestrates ingestion, preprocessing, ML prediction and storage.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogAnalysisService {

    private final LogEntryRepository logRepo;
    private final LogPreprocessingService preprocessor;
    private final PythonMLClientService mlClient;
    private final AlertService alertService;

    // ── Ingest API ─────────────────────────────────────────────────────────────

    @Transactional
    public LogEntry ingestLog(Dtos.LogIngestRequest req) {
        LogEntry entry = preprocessor.fromIngestRequest(req);
        entry = logRepo.save(entry);
        analyzeAndMark(entry);
        return entry;
    }

    @Transactional
    public LogEntry ingestRaw(String rawLine) {
        LogEntry entry = preprocessor.parseRawLog(rawLine);
        if (entry == null) return null;
        entry = logRepo.save(entry);
        analyzeAndMark(entry);
        return entry;
    }

    // ── Scheduled batch processor (every 60 s) ────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processUnanalyzedLogs() {
        List<LogEntry> unprocessed = logRepo.findByAnomalyFalseAndProcessedAtIsNull();
        if (unprocessed.isEmpty()) return;

        log.info("Batch processing {} unanalyzed log entries", unprocessed.size());
        unprocessed.forEach(this::analyzeAndMark);
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    public Dtos.DashboardStats getDashboardStats() {
        long total = logRepo.count();
        long anomalies = logRepo.countAnomalies();
        long last24h = logRepo.countLogsAfter(LocalDateTime.now().minusHours(24));

        Map<String, Long> byLevel = logRepo.countByLevel()
                .stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));

        List<Dtos.RecentAnomaly> recent = logRepo.findRecentAnomalies()
                .stream()
                .limit(10)
                .map(e -> Dtos.RecentAnomaly.builder()
                        .id(e.getId())
                        .timestamp(e.getTimestamp())
                        .level(e.getLevel())
                        .message(e.getMessage())
                        .score(e.getAnomalyScore() != null ? e.getAnomalyScore() : 0.0)
                        .build())
                .toList();

        return Dtos.DashboardStats.builder()
                .totalLogs(total)
                .totalAnomalies(anomalies)
                .logsLast24h(last24h)
                .logsByLevel(byLevel)
                .recentAnomalies(recent)
                .build();
    }

    public List<LogEntry> getAllAnomalies() {
        return logRepo.findByAnomalyTrue();
    }

    public List<LogEntry> getLatestLogs() {
        return logRepo.findLatest20();
    }

    // ── Core analysis helper ──────────────────────────────────────────────────

    private void analyzeAndMark(LogEntry entry) {
        try {
            Dtos.PredictionResponse prediction = mlClient.predict(entry);

            entry.setAnomalyScore(prediction.getScore());
            entry.setAnomaly(prediction.isAnomaly());
            entry.setProcessedAt(LocalDateTime.now());
            logRepo.save(entry);

            if (prediction.isAnomaly()) {
                log.warn("Anomaly flagged: log id={} score={}", entry.getId(), prediction.getScore());
                alertService.sendAlert(entry);
            }
        } catch (Exception ex) {
            log.error("Error analyzing log id={}: {}", entry.getId(), ex.getMessage());
        }
    }
}
