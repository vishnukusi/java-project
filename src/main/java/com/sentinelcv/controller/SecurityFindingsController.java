package com.sentinelcv.controller;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.repository.SecurityFindingRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Controller for viewing and filtering aggregated security findings across all modules.
 */
@Controller
@RequestMapping("/findings")
public class SecurityFindingsController {

    private final SecurityFindingRepository findingRepository;

    public SecurityFindingsController(SecurityFindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    @GetMapping
    public String listFindings(@RequestParam(value = "module", required = false) String module,
                               @RequestParam(value = "severity", required = false) String severity,
                               Model model) {
        List<SecurityFinding> findings;
        if (module != null && !module.isBlank()) {
            findings = findingRepository.findByModule(module.toUpperCase());
        } else if (severity != null && !severity.isBlank()) {
            findings = findingRepository.findBySeverity(FindingSeverity.valueOf(severity.toUpperCase()));
        } else {
            findings = findingRepository.findAll();
        }

        model.addAttribute("findings", findings);
        model.addAttribute("selectedModule", module);
        model.addAttribute("selectedSeverity", severity);
        return "findings/list";
    }
}
