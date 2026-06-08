package com.swp391.backend.controller;

import com.swp391.backend.service.SystemQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final SystemQueryService systemQueryService;

    public SystemController(SystemQueryService systemQueryService) {
        this.systemQueryService = systemQueryService;
    }

    @GetMapping({"/test", "/health"})
    public Map<String, Object> test() {
        return systemQueryService.health();
    }
}
