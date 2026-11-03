package com.sentinelcv.controller;

import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.repository.AuditReportRepository;
import com.sentinelcv.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Controller for generating and downloading consolidated audit reports (JSON, HTML, PDF).
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final AuditReportRepository reportRepository;

    public ReportController(ReportService reportService,
                            AuditReportRepository reportRepository) {
        this.reportService = reportService;
        this.reportRepository = reportRepository;
    }

    @GetMapping
    public String listReports(Model model) {
        List<AuditReport> reports = reportRepository.findAll();
        model.addAttribute("reports", reports);
        return "reports/list";
    }

    @PostMapping("/generate")
    public String generateReport(@RequestParam(value = "title", defaultValue = "SentinelCV Security Audit Report") String title,
                                 @RequestParam(value = "format", defaultValue = "JSON") String format,
                                 RedirectAttributes redirectAttributes) {
        try {
            AuditReport report = reportService.generateReport(title, format);
            redirectAttributes.addFlashAttribute("success",
                    String.format("Report '%s' (%s) generated successfully.", report.getTitle(), report.getFormat()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to generate report: " + e.getMessage());
        }
        return "redirect:/reports";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadReport(@PathVariable("id") Long id) {
        Optional<AuditReport> reportOpt = reportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AuditReport report = reportOpt.get();
        File file = new File(report.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if ("JSON".equalsIgnoreCase(report.getFormat())) {
            mediaType = MediaType.APPLICATION_JSON;
        } else if ("HTML".equalsIgnoreCase(report.getFormat())) {
            mediaType = MediaType.TEXT_HTML;
        } else if ("PDF".equalsIgnoreCase(report.getFormat())) {
            mediaType = MediaType.APPLICATION_PDF;
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
