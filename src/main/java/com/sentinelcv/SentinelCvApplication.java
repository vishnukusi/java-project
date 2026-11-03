package com.sentinelcv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SentinelCV - Offline Computer Vision Security and Integrity Assessment System.
 * 
 * Main application entry point. Initializes the Spring Boot application context
 * with async support for long-running audit operations.
 */
@SpringBootApplication
@EnableAsync
public class SentinelCvApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelCvApplication.class, args);
    }
}
