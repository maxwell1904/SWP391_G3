package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.MvpDemoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class FieldOperationController {
    private final MvpDemoService demoService;

    public FieldOperationController(MvpDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/fields")
    public Object fields() {
        return demoService.fields();
    }

    @GetMapping("/fields/{fieldId}")
    public Object fieldDetail(@PathVariable Long fieldId) {
        return demoService.fieldDetail(fieldId);
    }

    @GetMapping("/field-types")
    public Object fieldTypes() {
        return demoService.fieldTypes();
    }

    @GetMapping("/slots/search")
    public Object searchSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long fieldTypeId
    ) {
        return demoService.searchSlots(date, fieldTypeId);
    }

    @PostMapping("/slots/block")
    public Object blockSlot(@RequestBody ApiRequests.SlotBlock request) {
        return demoService.blockSlot(request);
    }

    @GetMapping("/services")
    public Object services() {
        return demoService.services();
    }

    @GetMapping("/issues")
    public Object issues() {
        return demoService.issues();
    }

    @PostMapping("/issues")
    public Object createIssue(@RequestBody ApiRequests.IssueCreate request) {
        return demoService.createIssue(request);
    }
}
