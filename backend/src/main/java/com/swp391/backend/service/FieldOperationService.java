package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.IssueStatus;
import com.swp391.backend.enums.NotificationType;
import com.swp391.backend.enums.SlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class FieldOperationService {
    private final DemoSupportService support;

    public FieldOperationService(DemoSupportService support) {
        this.support = support;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fields() {
        return support.fieldRepository.findByStatus(CommonStatus.active).stream()
                .map(support::fieldSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fieldDetail(Long fieldId) {
        FootballField field = support.getField(fieldId);
        List<Map<String, Object>> prices = support.fieldPriceRepository.findByField_FieldId(fieldId).stream()
                .map(price -> Map.<String, Object>of(
                        "dayType", price.getDayType(),
                        "startTime", price.getStartTime().toString(),
                        "endTime", price.getEndTime().toString(),
                        "price", price.getPrice()
                ))
                .toList();
        Map<String, Object> detail = new LinkedHashMap<>(support.fieldSummary(field));
        detail.put("prices", prices);
        detail.put("note", "Review text was intentionally removed from MVP schema; field detail shows pricing, services, and availability.");
        return detail;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fieldTypes() {
        return support.fieldTypeRepository.findAll().stream()
                .map(type -> Map.<String, Object>of(
                        "fieldTypeId", type.getFieldTypeId(),
                        "typeName", type.getTypeName(),
                        "playerCapacity", support.nvl(type.getPlayerCapacity(), 0)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchSlots(LocalDate date, Long fieldTypeId) {
        LocalDate targetDate = date == null ? LocalDate.now().plusDays(1) : date;
        return support.slotRepository.findBySlotDate(targetDate).stream()
                .filter(slot -> fieldTypeId == null || Objects.equals(slot.getField().getFieldType().getFieldTypeId(), fieldTypeId))
                .map(slot -> {
                    boolean reserved = support.bookingRepository.existsBySlotAndStatusIn(slot, DemoSupportService.ACTIVE_BOOKING_STATUSES);
                    BigDecimal fieldPrice = support.calculateFieldPrice(slot);
                    return Map.<String, Object>ofEntries(
                            Map.entry("slotId", slot.getSlotId()),
                            Map.entry("fieldId", slot.getField().getFieldId()),
                            Map.entry("fieldName", slot.getField().getFieldName()),
                            Map.entry("fieldType", slot.getField().getFieldType().getTypeName()),
                            Map.entry("slotDate", slot.getSlotDate().toString()),
                            Map.entry("startTime", slot.getStartTime().toString()),
                            Map.entry("endTime", slot.getEndTime().toString()),
                            Map.entry("status", reserved ? "booked" : slot.getStatus().name()),
                            Map.entry("available", slot.getStatus() == SlotStatus.available && !reserved),
                            Map.entry("price", fieldPrice)
                    );
                })
                .toList();
    }

    public Map<String, Object> blockSlot(ApiRequests.SlotBlock request) {
        FootballField field = support.getField(request.fieldId());
        Slot slot = new Slot();
        slot.setField(field);
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(LocalTime.parse(request.startTime()));
        slot.setEndTime(LocalTime.parse(request.endTime()));
        slot.setStatus(SlotStatus.blocked);
        slot.setBlockReason(request.blockReason());
        slot.setBlockNote(request.blockNote());
        if (request.createdById() != null) {
            slot.setCreatedBy(support.getUser(request.createdById()));
        }
        return support.slotSummary(support.slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> services() {
        return support.extraServiceRepository.findByStatus(CommonStatus.active).stream()
                .map(support::serviceSummary)
                .toList();
    }

    public Map<String, Object> createIssue(ApiRequests.IssueCreate request) {
        support.requireText(request.title(), "Issue title is required");
        Issue issue = new Issue();
        issue.setReporter(support.getUser(request.reporterId()));
        if (request.bookingId() != null) {
            issue.setBooking(support.getBooking(request.bookingId()));
        }
        if (request.fieldId() != null) {
            issue.setField(support.getField(request.fieldId()));
        }
        if (request.extraServiceId() != null) {
            issue.setExtraService(support.getExtraService(request.extraServiceId()));
        }
        if (request.assignedStaffId() != null) {
            issue.setAssignedStaff(support.getUser(request.assignedStaffId()));
        }
        issue.setTitle(request.title());
        issue.setDescription(request.description());
        return support.issueSummary(support.issueRepository.save(issue));
    }

    // Backlog owner: BaoNG - UC-23 Resolve field/service issue.
    public Map<String, Object> updateIssueStatus(Long issueId, ApiRequests.IssueStatusUpdate request) {
        Issue issue = support.issueRepository.findById(issueId)
                .orElseThrow(() -> support.notFound("Issue not found"));
        IssueStatus nextStatus = support.parseEnum(IssueStatus.class, request.status(), issue.getStatus());
        if (request.assignedStaffId() != null) {
            issue.setAssignedStaff(support.getUser(request.assignedStaffId()));
        }

        String resolutionNote = support.clean(request.resolutionNote());
        if (nextStatus == IssueStatus.resolved || nextStatus == IssueStatus.rejected) {
            support.requireText(resolutionNote, "Resolution note is required when closing an issue");
            issue.setResolvedAt(LocalDateTime.now());
        } else {
            issue.setResolvedAt(null);
        }
        issue.setResolutionNote(resolutionNote);
        issue.setStatus(nextStatus);

        support.notifyUser(
                issue.getReporter(),
                issue.getBooking(),
                NotificationType.issue,
                "Issue updated",
                "Issue \"" + issue.getTitle() + "\" is now " + nextStatus.name().replace('_', ' ') + "."
        );
        return support.issueSummary(issue);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issues() {
        return support.issueRepository.findAllByOrderByIssueIdDesc().stream()
                .map(support::issueSummary)
                .toList();
    }
}
