package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.AvailabilityAssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AvailabilityAssistantController {
    private final AvailabilityAssistantService availabilityAssistantService;

    public AvailabilityAssistantController(AvailabilityAssistantService availabilityAssistantService) {
        this.availabilityAssistantService = availabilityAssistantService;
    }

    /** UC-62: Gemini understands the question; UC-63 remains the source of truth for slots. */
    @PostMapping("/availability")
    public Object askForAvailability(@RequestBody ApiRequests.AssistantAvailability request) {
        return availabilityAssistantService.answerAvailabilityQuestion(request);
    }
}
