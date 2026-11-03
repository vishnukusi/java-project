package com.sentinelcv.integration;

import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReportServiceIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Test
    void testGenerateJsonReport() throws IOException {
        AuditReport report = reportService.generateReport("Integration Test JSON Report", "JSON");
        assertNotNull(report);
        assertEquals("JSON", report.getFormat());
        File file = new File(report.getFilePath());
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
    }

    @Test
    void testGenerateHtmlReport() throws IOException {
        AuditReport report = reportService.generateReport("Integration Test HTML Report", "HTML");
        assertNotNull(report);
        assertEquals("HTML", report.getFormat());
        File file = new File(report.getFilePath());
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
    }

    @Test
    void testGeneratePdfReport() throws IOException {
        AuditReport report = reportService.generateReport("Integration Test PDF Report", "PDF");
        assertNotNull(report);
        assertEquals("PDF", report.getFormat());
        File file = new File(report.getFilePath());
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
    }
}
