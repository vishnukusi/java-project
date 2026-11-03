package com.sentinelcv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sentinelcv.config.ApplicationConfig;
import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.repository.AuditReportRepository;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for generating comprehensive audit reports in JSON, HTML, and PDF formats.
 * Includes executive summaries, technical findings, evidence breakdowns, and actionable recommendations.
 */
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final RiskAssessmentService riskAssessmentService;
    private final DatasetRepository datasetRepository;
    private final DatasetAuditRepository datasetAuditRepository;
    private final MlModelRepository modelRepository;
    private final ModelAuditRepository modelAuditRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditReportRepository reportRepository;
    private final ApplicationConfig applicationConfig;
    private final ObjectMapper objectMapper;

    public ReportService(RiskAssessmentService riskAssessmentService,
                         DatasetRepository datasetRepository,
                         DatasetAuditRepository datasetAuditRepository,
                         MlModelRepository modelRepository,
                         ModelAuditRepository modelAuditRepository,
                         SecurityFindingRepository securityFindingRepository,
                         AuditReportRepository reportRepository,
                         ApplicationConfig applicationConfig,
                         ObjectMapper objectMapper) {
        this.riskAssessmentService = riskAssessmentService;
        this.datasetRepository = datasetRepository;
        this.datasetAuditRepository = datasetAuditRepository;
        this.modelRepository = modelRepository;
        this.modelAuditRepository = modelAuditRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.reportRepository = reportRepository;
        this.applicationConfig = applicationConfig;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generates a consolidated security audit report in the specified format (JSON, HTML, or PDF).
     */
    public AuditReport generateReport(String title, String format) throws IOException {
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        List<Dataset> datasets = datasetRepository.findAll();
        List<MlModel> models = modelRepository.findAll();
        List<DatasetAudit> datasetAudits = datasetAuditRepository.findAll();
        List<ModelAudit> modelAudits = modelAuditRepository.findAll();
        List<SecurityFinding> findings = securityFindingRepository.findAll();

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("title", title != null ? title : "SentinelCV Security Assessment Report");
        reportData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        reportData.put("assessmentDecision", assessment.overallDecision.name());
        reportData.put("compositeRiskScore", assessment.compositeRiskScore);
        reportData.put("criticalFindings", assessment.criticalCount);
        reportData.put("highFindings", assessment.highCount);
        reportData.put("mediumFindings", assessment.mediumCount);
        reportData.put("lowFindings", assessment.lowCount);
        reportData.put("infoFindings", assessment.infoCount);
        reportData.put("totalFindings", assessment.totalFindings);
        reportData.put("decisionJustifications", assessment.decisionJustifications);
        reportData.put("datasets", datasets);
        reportData.put("models", models);
        reportData.put("datasetAudits", datasetAudits);
        reportData.put("modelAudits", modelAudits);
        reportData.put("findings", findings);

        String filename = "report_" + System.currentTimeMillis();
        File reportsDir = new File(applicationConfig.getReportsDir());
        reportsDir.mkdirs();

        File outputFile;
        String formatUpper = format != null ? format.toUpperCase() : "JSON";

        if ("PDF".equals(formatUpper)) {
            outputFile = new File(reportsDir, filename + ".pdf");
            exportToPdf(reportData, findings, assessment, outputFile);
        } else if ("HTML".equals(formatUpper)) {
            outputFile = new File(reportsDir, filename + ".html");
            exportToHtml(reportData, findings, assessment, outputFile);
        } else {
            outputFile = new File(reportsDir, filename + ".json");
            objectMapper.writeValue(outputFile, reportData);
        }

        AuditReport report = new AuditReport();
        report.setTitle(title != null ? title : "Comprehensive System Audit Report");
        report.setReportType("CONSOLIDATED");
        report.setFormat(formatUpper);
        report.setFilePath(outputFile.getAbsolutePath());
        report.setAssessmentDecision(assessment.overallDecision.name());
        report.setTotalFindings(assessment.totalFindings);
        report.setCriticalCount(assessment.criticalCount);
        report.setHighCount(assessment.highCount);
        report.setMediumCount(assessment.mediumCount);
        report.setLowCount(assessment.lowCount);
        report.setInfoCount(assessment.infoCount);
        report.setSummary(String.format("Report generated for %d findings. Overall Decision: %s (Risk Score: %.1f).",
                assessment.totalFindings, assessment.overallDecision, assessment.compositeRiskScore));

        return reportRepository.save(report);
    }

    private void exportToPdf(Map<String, Object> data, List<SecurityFinding> findings,
                             RiskAssessmentService.SystemRiskAssessment assessment, File file) throws IOException {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.DARK_GRAY);
            document.add(new Paragraph("SentinelCV Security Assessment Report", titleFont));

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " | Offline Mode", subFont));
            document.add(Chunk.NEWLINE);

            // Executive Summary
            Font secFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            document.add(new Paragraph("Executive Summary", secFont));

            Color decisionColor = switch (assessment.overallDecision) {
                case QUARANTINE -> Color.RED;
                case REVIEW -> Color.ORANGE;
                case ACCEPT -> new Color(0, 150, 0);
            };
            Font decFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, decisionColor);
            document.add(new Paragraph("Final Assessment Decision: " + assessment.overallDecision.name(), decFont));
            document.add(new Paragraph("Composite Risk Score: " + String.format("%.1f / 100.0", assessment.compositeRiskScore)));
            document.add(new Paragraph("Decision Justification: " + String.join(" ", assessment.decisionJustifications)));
            document.add(Chunk.NEWLINE);

            // Severity Breakdown Table
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.addCell(new Phrase("Total"));
            table.addCell(new Phrase("Critical"));
            table.addCell(new Phrase("High"));
            table.addCell(new Phrase("Medium"));
            table.addCell(new Phrase("Low"));
            table.addCell(new Phrase("Info"));

            table.addCell(String.valueOf(assessment.totalFindings));
            table.addCell(String.valueOf(assessment.criticalCount));
            table.addCell(String.valueOf(assessment.highCount));
            table.addCell(String.valueOf(assessment.mediumCount));
            table.addCell(String.valueOf(assessment.lowCount));
            table.addCell(String.valueOf(assessment.infoCount));
            document.add(table);
            document.add(Chunk.NEWLINE);

            // Findings Table
            document.add(new Paragraph("Key Security Findings and Evidence", secFont));
            PdfPTable fTable = new PdfPTable(4);
            fTable.setWidthPercentage(100);
            fTable.setWidths(new float[]{2f, 1.5f, 4f, 2.5f});
            fTable.addCell(new Phrase("Finding ID"));
            fTable.addCell(new Phrase("Severity"));
            fTable.addCell(new Phrase("Description"));
            fTable.addCell(new Phrase("Action"));

            for (SecurityFinding f : findings) {
                fTable.addCell(f.getFindingId());
                PdfPCell sCell = new PdfPCell(new Phrase(f.getSeverity().name()));
                if (f.getSeverity().getWeight() >= 4) sCell.setBackgroundColor(new Color(255, 230, 230));
                fTable.addCell(sCell);
                fTable.addCell(f.getDescription());
                fTable.addCell(f.getRecommendedAction() != null ? f.getRecommendedAction() : "N/A");
            }
            document.add(fTable);

            document.close();
        } catch (Exception e) {
            throw new IOException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    private void exportToHtml(Map<String, Object> data, List<SecurityFinding> findings,
                              RiskAssessmentService.SystemRiskAssessment assessment, File file) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>SentinelCV Security Report</title>")
                .append("<style>")
                .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; color: #333; background: #f8f9fa; }")
                .append(".container { max-width: 900px; margin: auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }")
                .append("h1 { color: #1a202c; border-bottom: 2px solid #e2e8f0; padding-bottom: 10px; }")
                .append(".badge { padding: 4px 10px; border-radius: 4px; font-weight: bold; color: white; display: inline-block; }")
                .append(".badge-CRITICAL { background: #dc3545; } .badge-HIGH { background: #fd7e14; } .badge-MEDIUM { background: #ffc107; color: black; }")
                .append(".badge-LOW { background: #17a2b8; } .badge-INFO { background: #6c757d; }")
                .append(".decision { font-size: 1.4rem; padding: 12px; margin: 15px 0; border-radius: 6px; font-weight: bold; text-align: center; }")
                .append(".decision-ACCEPT { background: #d4edda; color: #155724; } .decision-REVIEW { background: #fff3cd; color: #856404; } .decision-QUARANTINE { background: #f8d7da; color: #721c24; }")
                .append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }")
                .append("th, td { border: 1px solid #e2e8f0; padding: 10px; text-align: left; font-size: 0.9rem; }")
                .append("th { background: #edf2f7; }")
                .append("</style></head><body><div class='container'>")
                .append("<h1>SentinelCV Security Assessment Report</h1>")
                .append("<p><strong>Generated:</strong> ").append(data.get("timestamp")).append(" | <em>Offline Evaluation</em></p>")
                .append("<div class='decision decision-").append(assessment.overallDecision.name()).append("'>")
                .append("Overall Decision: ").append(assessment.overallDecision.name())
                .append(" (Risk Score: ").append(String.format("%.1f", assessment.compositeRiskScore)).append(" / 100.0)</div>")
                .append("<p><strong>Decision Rationale:</strong> ").append(String.join("<br>", assessment.decisionJustifications)).append("</p>")
                .append("<h2>Severity Summary</h2>")
                .append("<p>Critical: <strong>").append(assessment.criticalCount).append("</strong> | High: <strong>").append(assessment.highCount)
                .append("</strong> | Medium: <strong>").append(assessment.mediumCount).append("</strong> | Low: <strong>").append(assessment.lowCount)
                .append("</strong> | Info: <strong>").append(assessment.infoCount).append("</strong></p>")
                .append("<h2>Detailed Security Findings</h2>")
                .append("<table><thead><tr><th>ID</th><th>Module</th><th>Severity</th><th>Description</th><th>Evidence</th><th>Recommended Action</th></tr></thead><tbody>");

        for (SecurityFinding f : findings) {
            html.append("<tr>")
                    .append("<td>").append(f.getFindingId()).append("</td>")
                    .append("<td>").append(f.getModule()).append("</td>")
                    .append("<td><span class='badge badge-").append(f.getSeverity().name()).append("'>").append(f.getSeverity().name()).append("</span></td>")
                    .append("<td>").append(f.getDescription()).append("</td>")
                    .append("<td>").append(f.getEvidence() != null ? f.getEvidence() : "").append("</td>")
                    .append("<td>").append(f.getRecommendedAction() != null ? f.getRecommendedAction() : "").append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></div></body></html>");

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }
}
