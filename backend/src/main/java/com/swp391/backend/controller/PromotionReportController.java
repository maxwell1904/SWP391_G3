package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.PromotionReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PromotionReportController {
    private final PromotionReportService promotionReportService;

    public PromotionReportController(PromotionReportService promotionReportService) {
        this.promotionReportService = promotionReportService;
    }

    @GetMapping("/promotions")
    public Object promotions(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return promotionReportService.promotions(includeInactive);
    }

    @PostMapping("/promotions")
    public Object createPromotion(@RequestBody ApiRequests.PromotionUpsert request) {
        return promotionReportService.createPromotion(request);
    }

    @PutMapping("/promotions/{promotionId}")
    public Object updatePromotion(@PathVariable Long promotionId, @RequestBody ApiRequests.PromotionUpsert request) {
        return promotionReportService.updatePromotion(promotionId, request);
    }

    @PostMapping("/promotions/apply-preview")
    public Object applyPromotionPreview(@RequestBody ApiRequests.PromotionApply request) {
        return promotionReportService.applyPromotionPreview(request);
    }

    @GetMapping("/membership/levels")
    public Object membershipLevels() {
        return promotionReportService.membershipLevels();
    }

    @GetMapping("/membership/{customerId}/progress")
    public Object membershipProgress(@PathVariable Long customerId) {
        return promotionReportService.membershipProgress(customerId);
    }

    @GetMapping("/reports")
    public Object reports() {
        return promotionReportService.reports();
    }

    @GetMapping("/settings")
    public Object settings(@RequestParam(required = false) String group) {
        return promotionReportService.settings(group);
    }

    @PutMapping("/settings/{key}")
    public Object updateSetting(@PathVariable String key, @RequestBody ApiRequests.SettingUpdate request) {
        return promotionReportService.updateSetting(key, request);
    }

    @GetMapping("/notifications/{userId}")
    public Object notifications(@PathVariable Long userId) {
        return promotionReportService.notifications(userId);
    }
}
