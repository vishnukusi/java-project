package com.sentinelcv.controller;

import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.repository.AuditHistoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Controller for viewing system-wide audit history and compliance trail.
 */
@Controller
@RequestMapping("/history")
public class AuditHistoryController {

    private final AuditHistoryRepository auditHistoryRepository;

    public AuditHistoryController(AuditHistoryRepository auditHistoryRepository) {
        this.auditHistoryRepository = auditHistoryRepository;
    }

    @GetMapping
    public String viewHistory(Model model) {
        List<AuditHistory> historyList = auditHistoryRepository.findAll();
        model.addAttribute("historyList", historyList);
        return "audit/history";
    }
}
