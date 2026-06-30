package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.IssueStatus;
import com.swp391.backend.enums.NotificationType;
import com.swp391.backend.enums.ServiceType;
import com.swp391.backend.enums.SlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
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
    public List<Map<String, Object>> managedFields() {
        return support.fieldRepository.findAll().stream()
                .sorted(Comparator.comparing(FootballField::getFieldId))
                .map(support::fieldSummary)
                .toList();
    }

    public Map<String, Object> createField(ApiRequests.FieldUpsert request) {
        FootballField field = new FootballField();
        applyFieldRequest(field, request);
        return support.fieldSummary(support.fieldRepository.save(field));
    }

    public Map<String, Object> updateField(Long fieldId, ApiRequests.FieldUpsert request) {
        FootballField field = support.getField(fieldId);
        applyFieldRequest(field, request);
        return support.fieldSummary(field);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fieldDetail(Long fieldId) {
        FootballField field = support.getField(fieldId);
        List<FieldPrice> activePrices = support.fieldPriceRepository.findByField_FieldId(fieldId).stream()
                .filter(price -> price.getStatus() == CommonStatus.active)
                .sorted(Comparator.comparing(FieldPrice::getDayType).thenComparing(FieldPrice::getStartTime))
                .toList();
        List<Map<String, Object>> prices = activePrices.stream().map(this::fieldPriceSummary).toList();
        BigDecimal minPrice = activePrices.stream().map(FieldPrice::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = activePrices.stream().map(FieldPrice::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<Map<String, Object>> nextAvailableSlots = nextAvailableSlots(fieldId);
        Map<String, Object> detail = new LinkedHashMap<>(support.fieldSummary(field));
        detail.put("prices", prices);
        detail.put("priceRange", Map.of("min", minPrice, "max", maxPrice));
        detail.put("services", support.extraServiceRepository.findByStatus(CommonStatus.active).stream()
                .map(support::serviceSummary)
                .toList());
        detail.put("availabilitySummary", Map.of(
                "nextOpenSlotCount", nextAvailableSlots.size(),
                "nextOpenSlots", nextAvailableSlots
        ));
        detail.put("reviews", List.of());
        detail.put("reviewSummary", "Reviews are not modelled in the MVP schema yet.");
        return detail;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> managedFieldPrices(Long fieldId) {
        support.getField(fieldId);
        return support.fieldPriceRepository.findByField_FieldId(fieldId).stream()
                .sorted(Comparator.comparing(FieldPrice::getDayType).thenComparing(FieldPrice::getStartTime))
                .map(this::fieldPriceSummary)
                .toList();
    }

    public Map<String, Object> createFieldPrice(Long fieldId, ApiRequests.FieldPriceUpsert request) {
        FieldPrice price = new FieldPrice();
        price.setField(support.getField(fieldId));
        applyFieldPriceRequest(price, request);
        return fieldPriceSummary(support.fieldPriceRepository.save(price));
    }

    public Map<String, Object> updateFieldPrice(Long fieldPriceId, ApiRequests.FieldPriceUpsert request) {
        FieldPrice price = support.fieldPriceRepository.findById(fieldPriceId)
                .orElseThrow(() -> support.notFound("Field price not found"));
        applyFieldPriceRequest(price, request);
        return fieldPriceSummary(price);
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
                .filter(slot -> slot.getField().getStatus() == CommonStatus.active)
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> managedServices() {
        return support.extraServiceRepository.findAll().stream()
                .sorted(Comparator.comparing(ExtraService::getExtraServiceId))
                .map(support::serviceSummary)
                .toList();
    }

    public Map<String, Object> createService(ApiRequests.ExtraServiceUpsert request) {
        ExtraService service = new ExtraService();
        applyServiceRequest(service, request);
        return support.serviceSummary(support.extraServiceRepository.save(service));
    }

    public Map<String, Object> updateService(Long serviceId, ApiRequests.ExtraServiceUpsert request) {
        ExtraService service = support.getExtraService(serviceId);
        applyServiceRequest(service, request);
        return support.serviceSummary(service);
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

    private void applyFieldRequest(FootballField field, ApiRequests.FieldUpsert request) {
        support.requireText(request.fieldName(), "Field name is required");
        support.requireText(request.location(), "Field location is required");
        support.requireText(request.surfaceType(), "Surface type is required");
        if (request.fieldTypeId() == null) {
            throw support.badRequest("Field type is required");
        }
        FieldType fieldType = support.fieldTypeRepository.findById(request.fieldTypeId())
                .orElseThrow(() -> support.notFound("Field type not found"));
        field.setFieldType(fieldType);
        field.setFieldName(support.clean(request.fieldName()));
        field.setDescription(support.clean(request.description()));
        field.setImageUrl(support.clean(request.imageUrl()));
        field.setLocation(support.clean(request.location()));
        field.setSurfaceType(support.clean(request.surfaceType()));
        field.setStatus(support.parseEnum(CommonStatus.class, request.status(), CommonStatus.active));
    }

    private void applyFieldPriceRequest(FieldPrice price, ApiRequests.FieldPriceUpsert request) {
        support.requireText(request.dayType(), "Day type is required");
        support.requireText(request.startTime(), "Start time is required");
        support.requireText(request.endTime(), "End time is required");
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw support.badRequest("Price must be greater than zero");
        }
        LocalTime startTime = LocalTime.parse(request.startTime());
        LocalTime endTime = LocalTime.parse(request.endTime());
        if (!startTime.isBefore(endTime)) {
            throw support.badRequest("Start time must be before end time");
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null && request.effectiveFrom().isAfter(request.effectiveTo())) {
            throw support.badRequest("Effective from must be before effective to");
        }
        String dayType = support.clean(request.dayType()).toLowerCase();
        if (!List.of("all", "weekday", "weekend").contains(dayType)) {
            throw support.badRequest("Day type must be all, weekday, or weekend");
        }
        price.setDayType(dayType);
        price.setStartTime(startTime);
        price.setEndTime(endTime);
        price.setPrice(support.money(request.price()));
        price.setEffectiveFrom(request.effectiveFrom());
        price.setEffectiveTo(request.effectiveTo());
        price.setStatus(support.parseEnum(CommonStatus.class, request.status(), CommonStatus.active));
    }

    private void applyServiceRequest(ExtraService service, ApiRequests.ExtraServiceUpsert request) {
        support.requireText(request.serviceName(), "Service name is required");
        support.requireText(request.unitName(), "Unit name is required");
        if (request.unitPrice() == null || request.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw support.badRequest("Unit price must be zero or greater");
        }
        if (request.stockQuantity() != null && request.stockQuantity() < 0) {
            throw support.badRequest("Stock quantity must be zero or greater");
        }
        if (request.maxQuantityPerBooking() != null && request.maxQuantityPerBooking() < 1) {
            throw support.badRequest("Max quantity per booking must be at least one");
        }
        service.setServiceName(support.clean(request.serviceName()));
        service.setServiceType(support.parseEnum(ServiceType.class, request.serviceType(), ServiceType.rental));
        service.setDescription(support.clean(request.description()));
        service.setUnitName(support.clean(request.unitName()));
        service.setUnitPrice(support.money(request.unitPrice()));
        service.setStockQuantity(request.stockQuantity());
        service.setMaxQuantityPerBooking(request.maxQuantityPerBooking());
        service.setStatus(support.parseEnum(CommonStatus.class, request.status(), CommonStatus.active));
    }

    private Map<String, Object> fieldPriceSummary(FieldPrice price) {
        return Map.<String, Object>ofEntries(
                Map.entry("fieldPriceId", price.getFieldPriceId()),
                Map.entry("fieldId", price.getField().getFieldId()),
                Map.entry("fieldName", price.getField().getFieldName()),
                Map.entry("dayType", price.getDayType()),
                Map.entry("startTime", price.getStartTime().toString()),
                Map.entry("endTime", price.getEndTime().toString()),
                Map.entry("price", price.getPrice()),
                Map.entry("effectiveFrom", price.getEffectiveFrom() == null ? "" : price.getEffectiveFrom().toString()),
                Map.entry("effectiveTo", price.getEffectiveTo() == null ? "" : price.getEffectiveTo().toString()),
                Map.entry("status", price.getStatus().name())
        );
    }

    private List<Map<String, Object>> nextAvailableSlots(Long fieldId) {
        LocalDate today = LocalDate.now();
        return java.util.stream.IntStream.rangeClosed(0, 7)
                .mapToObj(today::plusDays)
                .flatMap(date -> support.slotRepository.findBySlotDate(date).stream())
                .filter(slot -> Objects.equals(slot.getField().getFieldId(), fieldId))
                .filter(slot -> slot.getField().getStatus() == CommonStatus.active)
                .filter(slot -> slot.getStatus() == SlotStatus.available)
                .filter(slot -> !support.bookingRepository.existsBySlotAndStatusIn(slot, DemoSupportService.ACTIVE_BOOKING_STATUSES))
                .sorted(Comparator.comparing(Slot::getSlotDate).thenComparing(Slot::getStartTime))
                .limit(5)
                .map(slot -> Map.<String, Object>ofEntries(
                        Map.entry("slotId", slot.getSlotId()),
                        Map.entry("slotDate", slot.getSlotDate().toString()),
                        Map.entry("startTime", slot.getStartTime().toString()),
                        Map.entry("endTime", slot.getEndTime().toString()),
                        Map.entry("price", support.calculateFieldPrice(slot))
                ))
                .toList();
    }
}
