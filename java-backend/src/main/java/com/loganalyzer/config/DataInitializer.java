package com.loganalyzer.config;

import com.loganalyzer.model.LogEntry;
import com.loganalyzer.model.User;
import com.loganalyzer.repository.LogEntryRepository;
import com.loganalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final LogEntryRepository logRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedSampleLogs();
    }

    private void seedUsers() {
        if (!userRepo.existsByUsername("admin")) {
            userRepo.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .email("admin@loganalyzer.com")
                    .build());
            log.info("Seeded admin user (password: admin123)");
        }
        if (!userRepo.existsByUsername("user")) {
            userRepo.save(User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("user123"))
                    .role("USER")
                    .email("user@loganalyzer.com")
                    .build());
            log.info("Seeded demo user (password: user123)");
        }
    }

    private void seedSampleLogs() {
        if (logRepo.count() > 0) return; // already seeded

        LocalDateTime now = LocalDateTime.now();
        List<LogEntry> samples = List.of(
            makeLog(now.minusMinutes(60), "INFO",  "UserService",   "User 'alice' logged in successfully", false, 0.05),
            makeLog(now.minusMinutes(55), "INFO",  "OrderService",  "Order #1023 placed successfully", false, 0.04),
            makeLog(now.minusMinutes(50), "DEBUG", "CacheService",  "Cache hit for key: product:42", false, 0.02),
            makeLog(now.minusMinutes(45), "WARN",  "DatabasePool",  "Connection pool utilization at 75%", false, 0.38),
            makeLog(now.minusMinutes(40), "INFO",  "PaymentService","Payment processed for order #1023", false, 0.06),
            makeLog(now.minusMinutes(35), "ERROR", "AuthService",   "Invalid JWT token — authentication failed", true,  0.91),
            makeLog(now.minusMinutes(30), "INFO",  "UserService",   "User 'bob' registered successfully", false, 0.03),
            makeLog(now.minusMinutes(25), "WARN",  "ApiGateway",    "High response time detected: 2300ms on /api/orders", false, 0.45),
            makeLog(now.minusMinutes(20), "ERROR", "DatabaseService","Connection timeout after 30000ms — java.sql.SQLTimeoutException", true, 0.95),
            makeLog(now.minusMinutes(15), "INFO",  "SchedulerService","Daily cleanup job completed", false, 0.07),
            makeLog(now.minusMinutes(10), "ERROR", "InventoryService","OutOfMemoryError: Java heap space", true, 0.98),
            makeLog(now.minusMinutes(5),  "WARN",  "SecurityFilter","5 consecutive failed login attempts for IP 192.168.1.50", true, 0.82),
            makeLog(now.minusMinutes(2),  "INFO",  "HealthCheck",   "System health check passed", false, 0.03)
        );

        logRepo.saveAll(samples);
        log.info("Seeded {} sample log entries", samples.size());
    }

    private LogEntry makeLog(LocalDateTime ts, String level, String source,
                             String message, boolean anomaly, double score) {
        return LogEntry.builder()
                .timestamp(ts)
                .level(level)
                .source(source)
                .message(message)
                .anomaly(anomaly)
                .anomalyScore(score)
                .processedAt(ts.plusSeconds(1))
                .alertSent(anomaly)
                .build();
    }
}
