package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.MvpDemoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PromotionReportController {
    private final MvpDemoService demoService;

    public PromotionReportController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/promotions")
    public Object promotions() {
        return demoService.promotions();
    }

    @PostMapping("/promotions/apply-preview")
    public Object applyPromotionPreview(@RequestBody ApiRequests.PromotionApply request) {
        return demoService.applyPromotionPreview(request);
    }

    @GetMapping("/membership/levels")
    public Object membershipLevels() {
        return demoService.membershipLevels();
    }

    @GetMapping("/membership/{customerId}/progress")
    public Object membershipProgress(@PathVariable Long customerId) {
        return demoService.membershipProgress(customerId);
    }

    @GetMapping("/reports")
    public Object reports() {
        return demoService.reports();
    }

    @GetMapping("/settings")
    public Object settings(@RequestParam(required = false) String group) {
        return demoService.settings(group);
    }

    @PutMapping("/settings/{key}")
    public Object updateSetting(@PathVariable String key, @RequestBody ApiRequests.SettingUpdate request) {
        return demoService.updateSetting(key, request);
    }

    @GetMapping("/notifications/{userId}")
    public Object notifications(@PathVariable Long userId) {
        return demoService.notifications(userId);
    }
}
