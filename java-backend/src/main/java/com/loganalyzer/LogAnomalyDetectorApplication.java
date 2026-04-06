package com.loganalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogAnomalyDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogAnomalyDetectorApplication.class, args);
    }
}
