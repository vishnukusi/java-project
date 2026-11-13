package com.sentinelcv.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public landing page and the New Scan view. /dashboard stays in DashboardController. */
@Controller
public class PageController {

    // Shown in the UI so the limit always matches the Spring Boot configuration.
    @Value("${spring.servlet.multipart.max-file-size:not reported}")
    private String maxUploadSize;

    @GetMapping("/")
    public String landing() { return "index"; }

    @GetMapping("/scan")
    public String scan(Model model) {
        model.addAttribute("maxUploadSize", maxUploadSize);
        return "scan";
    }
}
