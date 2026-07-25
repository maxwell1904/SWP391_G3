package com.swp391.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Keeps the rolling availability window populated without operator data entry. */
@Service
public class SlotGenerationScheduler {
    private final SlotGenerationService slotGenerationService;

    public SlotGenerationScheduler(SlotGenerationService slotGenerationService) {
        this.slotGenerationService = slotGenerationService;
    }

    @Scheduled(cron = "${app.slot-generation-cron:0 10 0 * * *}")
    public void generateDailyAvailability() {
        slotGenerationService.generateRollingWindow();
    }
}
