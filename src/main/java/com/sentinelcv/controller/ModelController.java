package com.sentinelcv.controller;

import com.sentinelcv.config.ApplicationConfig;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.ModelFindingRepository;
import com.sentinelcv.service.ModelAuditService;
import com.sentinelcv.util.HashUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Controller for ML model management, upload, inspection, and behavioral audit execution.
 */
@Controller
@RequestMapping("/models")
public class ModelController {

    private static final Logger logger = LoggerFactory.getLogger(ModelController.class);

    private final MlModelRepository modelRepository;
    private final ModelAuditRepository modelAuditRepository;
    private final ModelFindingRepository modelFindingRepository;
    private final ModelAuditService modelAuditService;
    private final ApplicationConfig applicationConfig;
    private final com.sentinelcv.service.RiskAssessmentService riskAssessmentService;

    @org.springframework.beans.factory.annotation.Autowired
    public ModelController(MlModelRepository modelRepository,
                           ModelAuditRepository modelAuditRepository,
                           ModelFindingRepository modelFindingRepository,
                           ModelAuditService modelAuditService,
                           ApplicationConfig applicationConfig,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.sentinelcv.service.RiskAssessmentService riskAssessmentService) {
        this.modelRepository = modelRepository;
        this.modelAuditRepository = modelAuditRepository;
        this.modelFindingRepository = modelFindingRepository;
        this.modelAuditService = modelAuditService;
        this.applicationConfig = applicationConfig;
        this.riskAssessmentService = riskAssessmentService;
    }

    public ModelController(MlModelRepository modelRepository,
                           ModelAuditRepository modelAuditRepository,
                           ModelFindingRepository modelFindingRepository,
                           ModelAuditService modelAuditService,
                           ApplicationConfig applicationConfig) {
        this(modelRepository, modelAuditRepository, modelFindingRepository, modelAuditService, applicationConfig, null);
    }

    @GetMapping
    public String listModels(Model model) {
        List<MlModel> models = modelRepository.findAll();
        model.addAttribute("models", models);
        return "model/list";
    }

    @PostMapping("/upload")
    public String uploadModel(@RequestParam("name") String name,
                              @RequestParam("format") String formatStr,
                              @RequestParam("modelFile") MultipartFile file,
                              RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a valid model file.");
            return "redirect:/models";
        }

        try {
            ModelFormat format = ModelFormat.valueOf(formatStr.toUpperCase());
            String sanitizedFilename = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");

            Path modelsDir = Paths.get(applicationConfig.getModelsDir()).toAbsolutePath().normalize();
            Files.createDirectories(modelsDir);
            Path destPath = modelsDir.resolve(System.currentTimeMillis() + "_" + sanitizedFilename);
            file.transferTo(destPath.toAbsolutePath().toFile());

            String hash = HashUtil.sha256(destPath.toFile());

            MlModel mlModel = new MlModel(name, format, destPath.toAbsolutePath().toString());
            mlModel.setFileSizeBytes(file.getSize());
            mlModel.setSha256Hash(hash);
            mlModel = modelRepository.save(mlModel);
            if (riskAssessmentService != null) {
                riskAssessmentService.invalidateModelChecks(mlModel.getId());
            }

            redirectAttributes.addFlashAttribute("success", "Model '" + name + "' uploaded and registered successfully.");
            return "redirect:/models/" + mlModel.getId();

        } catch (Exception e) {
            logger.error("Model upload failed", e);
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/models";
        }
    }

    @GetMapping("/{id}")
    public String viewModel(@PathVariable("id") Long id, Model model) {
        Optional<MlModel> modelOpt = modelRepository.findById(id);
        if (modelOpt.isEmpty()) {
            return "redirect:/models";
        }
        MlModel mlModel = modelOpt.get();
        List<ModelAudit> audits = modelAuditRepository.findByModelId(id);

        model.addAttribute("model", mlModel);
        model.addAttribute("audits", audits);
        return "model/view";
    }

    @PostMapping("/{id}/audit")
    public String startAudit(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<MlModel> modelOpt = modelRepository.findById(id);
        if (modelOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Model not found.");
            return "redirect:/models";
        }

        List<ModelAudit> existingAudits = modelAuditRepository.findByModelId(id);
        boolean hasActive = existingAudits.stream().anyMatch(a ->
                a.getStatus() == com.sentinelcv.enums.AuditStatus.PENDING ||
                a.getStatus() == com.sentinelcv.enums.AuditStatus.RUNNING);
        if (hasActive) {
            redirectAttributes.addFlashAttribute("error", "An audit is already running or pending for this model. Please wait for it to complete.");
            return "redirect:/models/" + id;
        }

        ModelAudit audit = new ModelAudit(id);
        audit.setStatus(com.sentinelcv.enums.AuditStatus.PENDING);
        audit = modelAuditRepository.save(audit);

        try {
            // Execute audit asynchronously via Spring proxy
            modelAuditService.executeAuditAsync(audit.getId());
            redirectAttributes.addFlashAttribute("success", "Model audit started in background. Status tracking active.");
            return "redirect:/models/" + id + "/audits/" + audit.getId();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.error("Model audit task rejected due to executor saturation: {}", e.getMessage());
            audit.setStatus(com.sentinelcv.enums.AuditStatus.FAILED);
            audit.setErrorMessage("Server busy: audit queue is saturated. Please retry later.");
            audit.setCompletedAt(java.time.LocalDateTime.now());
            modelAuditRepository.save(audit);
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("model.baseline_inference", audit.getId(),
                        String.valueOf(id), modelOpt.get().getSha256Hash(), com.sentinelcv.enums.CheckExecutionStatus.FAILED, "Queue saturated");
                riskAssessmentService.recordCheckStatus("model.trigger_perturbation", audit.getId(),
                        String.valueOf(id), modelOpt.get().getSha256Hash(), com.sentinelcv.enums.CheckExecutionStatus.FAILED, "Queue saturated");
            }
            redirectAttributes.addFlashAttribute("error", "System is temporarily busy (audit queue saturated). The audit could not be started. Please retry shortly.");
            return "redirect:/models/" + id;
        }
    }

    @GetMapping("/{id}/audits/{auditId}")
    public String viewAuditDetails(@PathVariable("id") Long id,
                                   @PathVariable("auditId") Long auditId,
                                   Model model,
                                   HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");

        Optional<MlModel> modelOpt = modelRepository.findById(id);
        Optional<ModelAudit> auditOpt = modelAuditRepository.findById(auditId);

        if (modelOpt.isEmpty() || auditOpt.isEmpty() || !id.equals(auditOpt.get().getModelId())) {
            return "redirect:/models";
        }

        List<SecurityFinding> findings = modelFindingRepository.findByAuditId(auditId);

        model.addAttribute("model", modelOpt.get());
        model.addAttribute("audit", auditOpt.get());
        model.addAttribute("findings", findings);
        return "model/audit_detail";
    }

    @GetMapping("/{id}/audits/{auditId}/status")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> getAuditStatus(
            @PathVariable("id") Long id,
            @PathVariable("auditId") Long auditId) {
        Optional<ModelAudit> auditOpt = modelAuditRepository.findById(auditId);
        if (auditOpt.isEmpty() || !id.equals(auditOpt.get().getModelId())) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        ModelAudit audit = auditOpt.get();
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("auditId", audit.getId());
        resp.put("status", audit.getStatus() != null ? audit.getStatus().name() : "UNKNOWN");
        resp.put("decision", audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null);
        resp.put("summary", audit.getSummary());
        resp.put("criticalFindings", audit.getCriticalFindings());
        resp.put("totalFindings", audit.getTotalFindings());
        resp.put("errorMessage", audit.getErrorMessage());
        resp.put("startedAt", audit.getStartedAt());
        resp.put("completedAt", audit.getCompletedAt());
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(resp);
    }

    @PostMapping("/{id}/delete")
    public String deleteModel(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        modelRepository.deleteById(id);
        if (riskAssessmentService != null) {
            riskAssessmentService.invalidateModelChecks(id);
        }
        redirectAttributes.addFlashAttribute("success", "Model deleted successfully.");
        return "redirect:/models";
    }
}
