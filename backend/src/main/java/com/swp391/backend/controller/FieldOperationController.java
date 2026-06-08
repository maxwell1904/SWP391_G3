package com.swp391.backend.controller;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.service.FieldOperationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class FieldOperationController {
    private final FieldOperationService fieldOperationService;

    public FieldOperationController(FieldOperationService fieldOperationService) {
        this.fieldOperationService = fieldOperationService;
    }

    @GetMapping("/fields")
    public Object fields() {
        return fieldOperationService.fields();
    }

    @GetMapping("/fields/{fieldId}")
    public Object fieldDetail(@PathVariable Long fieldId) {
        return fieldOperationService.fieldDetail(fieldId);
    }

    @GetMapping("/field-types")
    public Object fieldTypes() {
        return fieldOperationService.fieldTypes();
    }

    @GetMapping("/slots/search")
    public Object searchSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long fieldTypeId
    ) {
        return fieldOperationService.searchSlots(date, fieldTypeId);
    }

    @PostMapping("/slots/block")
    public Object blockSlot(@RequestBody ApiRequests.SlotBlock request) {
        return fieldOperationService.blockSlot(request);
    }

    @GetMapping("/services")
    public Object services() {
        return fieldOperationService.services();
    }

    @GetMapping("/issues")
    public Object issues() {
        return fieldOperationService.issues();
    }

    @PostMapping("/issues")
    public Object createIssue(@RequestBody ApiRequests.IssueCreate request) {
        return fieldOperationService.createIssue(request);
    }

    @PutMapping("/issues/{issueId}/status")
    public Object updateIssueStatus(@PathVariable Long issueId, @RequestBody ApiRequests.IssueStatusUpdate request) {
        return fieldOperationService.updateIssueStatus(issueId, request);
    }
}
