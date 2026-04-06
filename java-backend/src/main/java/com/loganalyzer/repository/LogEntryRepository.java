package com.loganalyzer.repository;

import com.loganalyzer.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    List<LogEntry> findByAnomalyTrue();

    List<LogEntry> findByLevel(String level);

    List<LogEntry> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<LogEntry> findByAnomalyTrueAndAlertSentFalse();

    @Query("SELECT l FROM LogEntry l WHERE l.anomaly = true ORDER BY l.timestamp DESC")
    List<LogEntry> findRecentAnomalies();

    @Query("SELECT COUNT(l) FROM LogEntry l WHERE l.anomaly = true")
    long countAnomalies();

    @Query("SELECT COUNT(l) FROM LogEntry l WHERE l.timestamp >= :since")
    long countLogsAfter(@Param("since") LocalDateTime since);

    @Query("SELECT l.level, COUNT(l) FROM LogEntry l GROUP BY l.level")
    List<Object[]> countByLevel();

    @Query("SELECT l FROM LogEntry l ORDER BY l.timestamp DESC LIMIT 20")
    List<LogEntry> findLatest20();

    List<LogEntry> findByAnomalyFalseAndProcessedAtIsNull();
}
