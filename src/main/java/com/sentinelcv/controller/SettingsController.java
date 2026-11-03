package com.sentinelcv.controller;

import com.sentinelcv.repository.SettingRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Controller for configuring application-wide thresholds and assessment parameters.
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingRepository settingRepository;

    public SettingsController(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @GetMapping
    public String viewSettings(Model model) {
        Map<String, String> settings = settingRepository.getAll();
        model.addAttribute("settings", settings);
        return "settings/index";
    }

    @PostMapping("/update")
    public String updateSetting(@RequestParam("key") String key,
                                @RequestParam("value") String value,
                                RedirectAttributes redirectAttributes) {
        settingRepository.setValue(key, value.trim());
        redirectAttributes.addFlashAttribute("success", "Setting '" + key + "' updated successfully.");
        return "redirect:/settings";
    }
}
