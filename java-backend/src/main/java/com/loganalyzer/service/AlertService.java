package com.loganalyzer.service;

import com.loganalyzer.model.LogEntry;
import com.loganalyzer.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 5 – Alert System
 * Polls DB for unsent anomaly alerts and dispatches email notifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final LogEntryRepository logRepo;
    private final JavaMailSender mailSender;

    @Value("${alert.email.to:admin@example.com}")
    private String alertEmailTo;

    @Value("${alert.email.from:noreply@loganalyzer.com}")
    private String alertEmailFrom;

    @Value("${alert.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Runs every 30 seconds — picks up unsent anomaly alerts and notifies.
     */
    @Scheduled(fixedDelay = 30_000)
    public void sendPendingAlerts() {
        List<LogEntry> pending = logRepo.findByAnomalyTrueAndAlertSentFalse();
        if (pending.isEmpty()) return;

        log.info("Processing {} pending anomaly alerts", pending.size());
        pending.forEach(this::sendAlert);
    }

    public void sendAlert(LogEntry entry) {
        // Always log to console
        log.warn("🚨 ANOMALY DETECTED | id={} level={} score={:.2f} | {}",
                entry.getId(), entry.getLevel(), entry.getAnomalyScore(), entry.getMessage());

        // Optionally send email
        if (emailEnabled) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(alertEmailFrom);
                mail.setTo(alertEmailTo);
                mail.setSubject("🚨 Log Anomaly Detected — ID " + entry.getId());
                mail.setText(buildEmailBody(entry));
                mailSender.send(mail);
                log.info("Alert email sent for log id={}", entry.getId());
            } catch (Exception ex) {
                log.error("Failed to send alert email: {}", ex.getMessage());
            }
        }

        // Mark as sent
        entry.setAlertSent(true);
        logRepo.save(entry);
    }

    private String buildEmailBody(LogEntry e) {
        return String.format("""
                Log Anomaly Detected
                ====================
                ID        : %d
                Timestamp : %s
                Level     : %s
                Source    : %s
                Score     : %.4f
                Message   : %s
                """,
                e.getId(), e.getTimestamp(), e.getLevel(),
                e.getSource(), e.getAnomalyScore(), e.getMessage());
    }
}
