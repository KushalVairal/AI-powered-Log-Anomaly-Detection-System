package com.loganalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 20)
    private String level; // INFO, WARN, ERROR, DEBUG

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 200)
    private String source; // class/service that generated the log

    @Column(length = 100)
    private String threadName;

    @Column(length = 100)
    private String requestId;

    @Column(nullable = false)
    @Builder.Default
    private boolean anomaly = false;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "alert_sent")
    @Builder.Default
    private boolean alertSent = false;

    @Column(name = "raw_log", length = 1000)
    private String rawLog;
}
