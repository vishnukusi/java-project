package com.sentinelcv.controller;

import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.ProvenanceVerification;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.ProvenanceVerificationRepository;
import com.sentinelcv.service.InferenceProvenanceService;
import com.sentinelcv.service.RiskAssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for inference provenance verification, record import,
 * hash chain verification, and replay detection.
 */
@Controller
@RequestMapping("/inference")
public class InferenceController {

    private static final Logger logger = LoggerFactory.getLogger(InferenceController.class);

    private final InferenceRecordRepository recordRepository;
    private final ProvenanceVerificationRepository verificationRepository;
    private final InferenceProvenanceService provenanceService;
    private final RiskAssessmentService riskAssessmentService;

    @Autowired
    public InferenceController(InferenceRecordRepository recordRepository,
                               ProvenanceVerificationRepository verificationRepository,
                               InferenceProvenanceService provenanceService,
                               RiskAssessmentService riskAssessmentService) {
        this.recordRepository = recordRepository;
        this.verificationRepository = verificationRepository;
        this.provenanceService = provenanceService;
        this.riskAssessmentService = riskAssessmentService;
    }

    public InferenceController(InferenceRecordRepository recordRepository,
                               ProvenanceVerificationRepository verificationRepository,
                               InferenceProvenanceService provenanceService) {
        this(recordRepository, verificationRepository, provenanceService, null);
    }

    @GetMapping
    public String viewProvenance(Model model) {
        List<InferenceRecord> records = recordRepository.findAllOrderedBySequence();
        List<ProvenanceVerification> verifications = verificationRepository.findAll();

        Map<String, ProvenanceVerification> verMap = new HashMap<>();
        for (ProvenanceVerification v : verifications) {
            verMap.put(v.getRecordId(), v);
        }

        model.addAttribute("records", records);
        model.addAttribute("verifications", verMap);
        return "inference/provenance";
    }

    @PostMapping("/import")
    public String importRecords(@RequestParam(value = "jsonFile", required = false) MultipartFile file,
                                @RequestParam(value = "jsonContent", required = false) String jsonContent,
                                RedirectAttributes redirectAttributes) {
        try {
            String content = "";
            if (file != null && !file.isEmpty()) {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (jsonContent != null && !jsonContent.isBlank()) {
                content = jsonContent.trim();
            } else {
                redirectAttributes.addFlashAttribute("error", "Please provide a JSON file or paste JSON records.");
                return "redirect:/inference";
            }

            if (content.isBlank() || "null".equalsIgnoreCase(content)) {
                redirectAttributes.addFlashAttribute("error", "No valid inference records provided in submission.");
                return "redirect:/inference";
            }

            InferenceValidator.VerificationBatchResult result = provenanceService.importAndVerify(content);
            redirectAttributes.addFlashAttribute("success",
                    String.format("Imported and verified %d records. Verified: %d, Failed: %d, Broken chains: %d, Replays: %d.",
                            result.totalRecordsChecked, result.verifiedCount, result.failedCount,
                            result.brokenChainLinks, result.replayAttempts));

        } catch (Exception e) {
            logger.error("Inference records import failed", e);
            redirectAttributes.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }
        return "redirect:/inference";
    }

    @PostMapping("/verify-all")
    public String verifyAll(RedirectAttributes redirectAttributes) {
        try {
            InferenceValidator.VerificationBatchResult result = provenanceService.verifyAllRecords();
            redirectAttributes.addFlashAttribute("success",
                    String.format("Provenance verification complete. %d/%d verified. Broken chains: %d, Replay attempts: %d.",
                            result.verifiedCount, result.totalRecordsChecked, result.brokenChainLinks, result.replayAttempts));
        } catch (Exception e) {
            logger.error("Provenance verification failed", e);
            redirectAttributes.addFlashAttribute("error", "Verification failed: " + e.getMessage());
        }
        return "redirect:/inference";
    }

    @PostMapping("/clear")
    public String clearRecords(RedirectAttributes redirectAttributes) {
        recordRepository.deleteAll();
        if (riskAssessmentService != null) {
            riskAssessmentService.invalidateProvenanceChecks();
        }
        redirectAttributes.addFlashAttribute("success", "Inference records and verifications cleared.");
        return "redirect:/inference";
    }
}
