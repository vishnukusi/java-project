package com.sentinelcv.controller;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DriftAudit;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.DriftAuditRepository;
import com.sentinelcv.service.DriftDetectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controller for distribution shift comparison between reference and evaluation datasets.
 */
@Controller
@RequestMapping("/drift")
public class DriftController {

    private final DatasetRepository datasetRepository;
    private final DriftAuditRepository driftAuditRepository;
    private final DriftDetectionService driftDetectionService;

    public DriftController(DatasetRepository datasetRepository,
                           DriftAuditRepository driftAuditRepository,
                           DriftDetectionService driftDetectionService) {
        this.datasetRepository = datasetRepository;
        this.driftAuditRepository = driftAuditRepository;
        this.driftDetectionService = driftDetectionService;
    }

    @GetMapping
    public String viewDrift(Model model) {
        List<Dataset> datasets = datasetRepository.findAll();
        List<DriftAudit> driftAudits = driftAuditRepository.findAll();

        model.addAttribute("datasets", datasets);
        model.addAttribute("driftAudits", driftAudits);
        return "drift/analysis";
    }

    @PostMapping("/run")
    public String runDriftAudit(@RequestParam("referenceDatasetId") Long refId,
                                @RequestParam("evaluationDatasetId") Long evalId,
                                RedirectAttributes redirectAttributes) {
        if (refId.equals(evalId)) {
            redirectAttributes.addFlashAttribute("error", "Reference and evaluation datasets must be different.");
            return "redirect:/drift";
        }

        try {
            DriftAudit audit = driftDetectionService.executeDriftAudit(refId, evalId);
            redirectAttributes.addFlashAttribute("success",
                    "Distribution shift analysis completed. Drift detected: " + audit.isDriftDetected());
            return "redirect:/drift/" + audit.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Drift audit failed: " + e.getMessage());
            return "redirect:/drift";
        }
    }

    @GetMapping("/{id}")
    public String viewDriftDetails(@PathVariable("id") Long id, Model model) {
        Optional<DriftAudit> auditOpt = driftAuditRepository.findById(id);
        if (auditOpt.isEmpty()) {
            return "redirect:/drift";
        }

        model.addAttribute("audit", auditOpt.get());
        return "drift/detail";
    }
}
