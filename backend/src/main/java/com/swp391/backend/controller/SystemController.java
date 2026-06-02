package com.swp391.backend.controller;

import com.swp391.backend.service.MvpDemoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final MvpDemoService demoService;

    public SystemController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/test")
    public Map<String, Object> test() {
        return demoService.health();
    }
}
