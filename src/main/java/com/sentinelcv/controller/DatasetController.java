package com.sentinelcv.controller;

import com.sentinelcv.config.ApplicationConfig;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetFindingRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.service.DatasetAuditService;
import com.sentinelcv.util.ArchiveUtil;
import com.sentinelcv.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for dataset management, safe archive upload, and asynchronous audit execution.
 */
@Controller
@RequestMapping("/datasets")
public class DatasetController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetController.class);

    private final DatasetRepository datasetRepository;
    private final DatasetAuditRepository datasetAuditRepository;
    private final DatasetFindingRepository datasetFindingRepository;
    private final DatasetAuditService datasetAuditService;
    private final ApplicationConfig applicationConfig;
    private final com.sentinelcv.service.RiskAssessmentService riskAssessmentService;

    @org.springframework.beans.factory.annotation.Autowired
    public DatasetController(DatasetRepository datasetRepository,
                             DatasetAuditRepository datasetAuditRepository,
                             DatasetFindingRepository datasetFindingRepository,
                             DatasetAuditService datasetAuditService,
                             ApplicationConfig applicationConfig,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.sentinelcv.service.RiskAssessmentService riskAssessmentService) {
        this.datasetRepository = datasetRepository;
        this.datasetAuditRepository = datasetAuditRepository;
        this.datasetFindingRepository = datasetFindingRepository;
        this.datasetAuditService = datasetAuditService;
        this.applicationConfig = applicationConfig;
        this.riskAssessmentService = riskAssessmentService;
    }

    public DatasetController(DatasetRepository datasetRepository,
                             DatasetAuditRepository datasetAuditRepository,
                             DatasetFindingRepository datasetFindingRepository,
                             DatasetAuditService datasetAuditService,
                             ApplicationConfig applicationConfig) {
        this(datasetRepository, datasetAuditRepository, datasetFindingRepository, datasetAuditService, applicationConfig, null);
    }

    @GetMapping
    public String listDatasets(Model model) {
        List<Dataset> datasets = datasetRepository.findAll();
        model.addAttribute("datasets", datasets);
        return "dataset/list";
    }

    @PostMapping("/upload")
    public String uploadDataset(@RequestParam("name") String name,
                                @RequestParam("format") String formatStr,
                                @RequestParam("archive") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a non-empty dataset ZIP file.");
            return "redirect:/datasets";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            redirectAttributes.addFlashAttribute("error", "Invalid file extension: only .zip archives are permitted.");
            return "redirect:/datasets";
        }

        Path tempZip = null;
        Path targetDir = null;

        try {
            DatasetFormat format = DatasetFormat.valueOf(formatStr.toUpperCase());
            String sanitizedName = name.replaceAll("[^a-zA-Z0-9_-]", "_");

            // Save uploaded zip file temporarily
            Path uploadDir = Paths.get(applicationConfig.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            tempZip = Files.createTempFile(uploadDir, "upload_", "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_"));
            file.transferTo(tempZip.toAbsolutePath().toFile());

            // Validate ZIP magic bytes
            if (!ArchiveUtil.hasValidZipMagicBytes(tempZip.toFile())) {
                redirectAttributes.addFlashAttribute("error", "Upload rejected: File is not a valid ZIP archive (magic bytes mismatch).");
                return "redirect:/datasets";
            }

            // Compute hash of the archive
            String archiveHash = HashUtil.sha256(tempZip.toFile());

            // Extract into datasets directory safely
            targetDir = Paths.get(applicationConfig.getDatasetsDir()).toAbsolutePath().normalize().resolve(sanitizedName + "_" + System.currentTimeMillis());
            ArchiveUtil.extractZipSafely(tempZip.toFile(), targetDir);

            // Compute deterministic directory content hash of extracted dataset (fail closed if hashing fails)
            String dirHash = HashUtil.calculateDatasetDirectoryHash(targetDir.toFile());

            // Register dataset in database only after full extraction succeeded
            Dataset dataset = new Dataset(name, format, targetDir.toAbsolutePath().toString());
            dataset.setFileSizeBytes(file.getSize());
            dataset.setSha256Hash(dirHash);
            dataset = datasetRepository.save(dataset);
            if (riskAssessmentService != null) {
                riskAssessmentService.invalidateDatasetChecks(dataset.getId());
            }

            redirectAttributes.addFlashAttribute("success", "Dataset '" + name + "' uploaded and verified successfully.");
            return "redirect:/datasets/" + dataset.getId();

        } catch (Exception e) {
            logger.error("Dataset upload or extraction failed: {}", e.getMessage());
            // Failure recovery: Ensure partially extracted files are destroyed
            if (targetDir != null) {
                ArchiveUtil.deleteDirectoryRecursively(targetDir);
            }
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/datasets";
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException e) {
                    logger.warn("Could not delete temporary zip {}: {}", tempZip, e.getMessage());
                }
            }
        }
    }

    @GetMapping("/{id}")
    public String viewDataset(@PathVariable("id") Long id, Model model) {
        Optional<Dataset> datasetOpt = datasetRepository.findById(id);
        if (datasetOpt.isEmpty()) {
            return "redirect:/datasets";
        }
        Dataset dataset = datasetOpt.get();
        List<DatasetAudit> audits = datasetAuditRepository.findByDatasetId(id);

        model.addAttribute("dataset", dataset);
        model.addAttribute("audits", audits);
        return "dataset/view";
    }

    @PostMapping("/{id}/audit")
    public String startAudit(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Dataset> datasetOpt = datasetRepository.findById(id);
        if (datasetOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Dataset not found.");
            return "redirect:/datasets";
        }

        List<DatasetAudit> existingAudits = datasetAuditRepository.findByDatasetId(id);
        boolean hasActive = existingAudits.stream().anyMatch(a ->
                a.getStatus() == AuditStatus.PENDING || a.getStatus() == AuditStatus.RUNNING);
        if (hasActive) {
            redirectAttributes.addFlashAttribute("error", "An audit is already running or pending for this dataset. Please wait for it to complete.");
            return "redirect:/datasets/" + id;
        }

        DatasetAudit audit = new DatasetAudit(id);
        audit.setStatus(AuditStatus.PENDING);
        audit = datasetAuditRepository.save(audit);

        try {
            // Execute audit asynchronously via Spring proxy
            datasetAuditService.executeAuditAsync(audit.getId());
            redirectAttributes.addFlashAttribute("success", "Dataset audit started in background. Status tracking active.");
            return "redirect:/datasets/" + id + "/audits/" + audit.getId();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.error("Dataset audit task rejected due to executor saturation: {}", e.getMessage());
            audit.setStatus(AuditStatus.FAILED);
            audit.setErrorMessage("Server busy: audit queue is saturated. Please retry later.");
            audit.setCompletedAt(java.time.LocalDateTime.now());
            datasetAuditRepository.save(audit);
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        String.valueOf(id), datasetOpt.get().getSha256Hash(), com.sentinelcv.enums.CheckExecutionStatus.FAILED, "Queue saturated");
            }
            redirectAttributes.addFlashAttribute("error", "System is temporarily busy (audit queue saturated). The audit could not be started. Please retry shortly.");
            return "redirect:/datasets/" + id;
        }
    }

    @GetMapping("/{id}/audits/{auditId}")
    public String viewAuditDetails(@PathVariable("id") Long id,
                                   @PathVariable("auditId") Long auditId,
                                   Model model) {
        Optional<Dataset> datasetOpt = datasetRepository.findById(id);
        Optional<DatasetAudit> auditOpt = datasetAuditRepository.findById(auditId);

        if (datasetOpt.isEmpty() || auditOpt.isEmpty()) {
            return "redirect:/datasets";
        }

        List<SecurityFinding> findings = datasetFindingRepository.findByAuditId(auditId);

        model.addAttribute("dataset", datasetOpt.get());
        model.addAttribute("audit", auditOpt.get());
        model.addAttribute("findings", findings);
        return "dataset/audit_detail";
    }

    @GetMapping("/{id}/audits/{auditId}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAuditStatus(@PathVariable("id") Long id,
                                                              @PathVariable("auditId") Long auditId) {
        Optional<DatasetAudit> auditOpt = datasetAuditRepository.findById(auditId);
        if (auditOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DatasetAudit audit = auditOpt.get();
        Map<String, Object> resp = new HashMap<>();
        resp.put("auditId", audit.getId());
        resp.put("status", audit.getStatus() != null ? audit.getStatus().name() : "UNKNOWN");
        resp.put("decision", audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null);
        resp.put("summary", audit.getSummary());
        resp.put("criticalFindings", audit.getCriticalFindings());
        resp.put("totalFindings", audit.getTotalFindings());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/delete")
    public String deleteDataset(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        datasetRepository.deleteById(id);
        if (riskAssessmentService != null) {
            riskAssessmentService.invalidateDatasetChecks(id);
        }
        redirectAttributes.addFlashAttribute("success", "Dataset deleted successfully.");
        return "redirect:/datasets";
    }
}
