package com.swp391.backend.service;

import com.swp391.backend.dto.ApiRequests;
import com.swp391.backend.entity.*;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.IssueStatus;
import com.swp391.backend.enums.NotificationType;
import com.swp391.backend.enums.ServiceType;
import com.swp391.backend.enums.SlotStatus;
import com.swp391.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class FieldOperationService {
    private final DomainSupportService support;
    private final SlotGenerationService slotGenerationService;

    public FieldOperationService(DomainSupportService support, SlotGenerationService slotGenerationService) {
        this.support = support;
        this.slotGenerationService = slotGenerationService;
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

    public Map<String, Object> fieldDetail(Long fieldId) {
        FootballField field = support.getField(fieldId);
        if (field.getStatus() != CommonStatus.active) throw support.notFound("Field not found");
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
        detail.put("reviewSummary", "Reviews are not available for this system.");
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

    public List<Map<String, Object>> searchSlots(LocalDate date, Long fieldTypeId) {
        LocalDate targetDate = date == null ? LocalDate.now().plusDays(1) : date;
        if (targetDate.isBefore(LocalDate.now())) throw support.badRequest("Search date cannot be in the past");
        slotGenerationService.ensureDate(targetDate);
        return support.slotRepository.findBySlotDate(targetDate).stream()
                .filter(slot -> slot.getField().getStatus() == CommonStatus.active)
                .filter(support::isSlotStartInFuture)
                .filter(slot -> fieldTypeId == null || Objects.equals(slot.getField().getFieldType().getFieldTypeId(), fieldTypeId))
                .flatMap(slot -> support.findFieldPrice(slot).stream().map(fieldPrice -> {
                    boolean reserved = support.bookingRepository.existsBySlotAndStatusIn(slot, DomainSupportService.ACTIVE_BOOKING_STATUSES);
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
                }))
                .toList();
    }

    public Map<String, Object> blockSlot(ApiRequests.SlotBlock request) {
        requireCurrentStaff();
        if (request.slotDate() == null) throw support.badRequest("Slot date is required");
        if (request.slotDate().isBefore(LocalDate.now())) throw support.badRequest("Past slots cannot be blocked");
        support.requireText(request.startTime(), "Start time is required");
        support.requireText(request.endTime(), "End time is required");
        support.requireText(request.blockReason(), "Block reason is required");
        FootballField field = support.getField(request.fieldId());
        LocalTime startTime = LocalTime.parse(request.startTime());
        LocalTime endTime = LocalTime.parse(request.endTime());
        if (!startTime.isBefore(endTime)) throw support.badRequest("Start time must be before end time");
        List<Slot> overlaps = support.slotRepository.findByField_FieldIdAndSlotDate(field.getFieldId(), request.slotDate()).stream()
                .filter(existing -> startTime.isBefore(existing.getEndTime()) && endTime.isAfter(existing.getStartTime()))
                .toList();
        if (overlaps.stream().anyMatch(slot ->
                support.bookingRepository.existsBySlotAndStatusIn(slot, DomainSupportService.ACTIVE_BOOKING_STATUSES))) {
            throw support.badRequest("An active booking exists inside this blocked time range");
        }
        if (overlaps.isEmpty()) {
            Slot exception = new Slot();
            exception.setField(field);
            exception.setSlotDate(request.slotDate());
            exception.setStartTime(startTime);
            exception.setEndTime(endTime);
            exception.setCreatedBy(currentUser());
            overlaps = List.of(exception);
        }
        for (Slot slot : overlaps) {
            slot.setStatus(SlotStatus.blocked);
            slot.setBlockReason(support.clean(request.blockReason()));
            slot.setBlockNote(support.clean(request.blockNote()));
        }
        List<Slot> blocked = support.slotRepository.saveAll(overlaps);
        Map<String, Object> response = new LinkedHashMap<>(support.slotSummary(blocked.get(0)));
        response.put("blockedSlotCount", blocked.size());
        return response;
    }

    public Map<String, Object> unblockSlot(Long slotId) {
        requireCurrentStaff();
        Slot slot = support.getSlot(slotId);
        if (slot.getStatus() != SlotStatus.blocked) {
            throw support.badRequest("Only blocked slots can be unblocked");
        }
        slot.setStatus(SlotStatus.available);
        slot.setBlockReason(null);
        slot.setBlockNote(null);
        return support.slotSummary(slot);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> operationCalendar(LocalDate date) {
        requireCurrentStaff();
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        slotGenerationService.ensureDate(targetDate);
        Map<Long, Booking> bookingsBySlot = support.bookingRepository.findBySlot_SlotDateOrderBySlot_StartTimeAsc(targetDate).stream()
                .collect(java.util.stream.Collectors.toMap(booking -> booking.getSlot().getSlotId(), booking -> booking, (first, ignored) -> first));
        return support.slotRepository.findBySlotDate(targetDate).stream()
                .sorted(Comparator.comparing((Slot slot) -> slot.getField().getFieldName())
                        .thenComparing(Slot::getStartTime))
                .map(slot -> {
                    Booking booking = bookingsBySlot.get(slot.getSlotId());
                    Map<String, Object> item = new LinkedHashMap<>(support.slotSummary(slot));
                    item.put("booking", booking == null ? null : support.bookingSummary(booking));
                    item.put("operationalStatus", booking != null ? booking.getStatus().name() : slot.getStatus().name());
                    return item;
                })
                .toList();
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
        support.requireText(request.description(), "Issue description is required");
        AppUser reporter = currentUser();
        if (!isStaff(reporter) && !"Customer".equalsIgnoreCase(reporter.getRole().getRoleName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only customers or venue staff can report an issue");
        }
        Issue issue = new Issue();
        issue.setReporter(reporter);
        Booking relatedBooking = null;
        if (request.bookingId() != null) {
            relatedBooking = support.getBooking(request.bookingId());
            Long ownerId = relatedBooking.getCustomer() == null ? null : relatedBooking.getCustomer().getUserId();
            if (!isOperator() && !Objects.equals(ownerId, reporter.getUserId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only report issues for your own booking");
            }
            issue.setBooking(relatedBooking);
        }
        if (request.fieldId() != null) {
            FootballField field = support.getField(request.fieldId());
            if (relatedBooking != null && !Objects.equals(
                    relatedBooking.getSlot().getField().getFieldId(), field.getFieldId())) {
                throw support.badRequest("The selected field does not belong to the related booking");
            }
            issue.setField(field);
        }
        if (request.extraServiceId() != null) {
            ExtraService service = support.getExtraService(request.extraServiceId());
            if (relatedBooking != null && support.bookingServiceItemRepository
                    .findByBooking_BookingId(relatedBooking.getBookingId()).stream()
                    .noneMatch(item -> Objects.equals(item.getExtraService().getExtraServiceId(), service.getExtraServiceId()))) {
                throw support.badRequest("The selected service does not belong to the related booking");
            }
            issue.setExtraService(service);
        }
        if (isStaff(reporter)) {
            issue.setAssignedStaff(reporter);
        }
        issue.setTitle(support.clean(request.title()));
        issue.setDescription(support.clean(request.description()));
        return support.issueSummary(support.issueRepository.save(issue));
    }

    // Backlog owner: BaoNG - UC-23 Resolve field/service issue.
    public Map<String, Object> updateIssueStatus(Long issueId, ApiRequests.IssueStatusUpdate request) {
        AppUser operator = currentUser();
        if (!isStaff(operator)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Venue staff access is required to handle issues");
        }
        Issue issue = support.issueRepository.findById(issueId)
                .orElseThrow(() -> support.notFound("Issue not found"));
        IssueStatus nextStatus = support.parseEnum(IssueStatus.class, request.status(), issue.getStatus());
        requireValidIssueTransition(issue.getStatus(), nextStatus);
        if (issue.getAssignedStaff() == null) {
            issue.setAssignedStaff(operator);
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
        AppUser requester = currentUser();
        boolean operator = "Staff".equalsIgnoreCase(requester.getRole().getRoleName())
                || "Admin".equalsIgnoreCase(requester.getRole().getRoleName());
        return support.issueRepository.findAllByOrderByIssueIdDesc().stream()
                .filter(issue -> operator
                        || issue.getReporter().getUserId().equals(requester.getUserId()))
                .map(support::issueSummary)
                .toList();
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        return user.getAppUser();
    }

    private boolean isOperator() {
        String role = currentUser().getRole().getRoleName();
        return "Staff".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
    }

    private boolean isStaff(AppUser user) {
        return "Staff".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private void requireValidIssueTransition(IssueStatus current, IssueStatus next) {
        boolean valid = switch (current) {
            case open -> next == IssueStatus.in_progress || next == IssueStatus.resolved || next == IssueStatus.rejected;
            case in_progress -> next == IssueStatus.resolved || next == IssueStatus.rejected;
            case resolved, rejected -> false;
        };
        if (!valid) {
            throw support.badRequest("Issue cannot change from " + current + " to " + next);
        }
    }

    private void requireOperator() {
        if (!isOperator()) throw new ApiException(HttpStatus.FORBIDDEN, "Staff or administrator access is required");
    }

    private AppUser requireCurrentStaff() {
        AppUser user = currentUser();
        if (!isStaff(user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Venue staff access is required");
        }
        return user;
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
        validateFieldPriceOverlap(price);
    }

    private void validateFieldPriceOverlap(FieldPrice candidate) {
        if (candidate.getStatus() != CommonStatus.active) return;
        boolean overlaps = support.fieldPriceRepository.findByField_FieldId(candidate.getField().getFieldId()).stream()
                .filter(existing -> candidate.getFieldPriceId() == null || !existing.getFieldPriceId().equals(candidate.getFieldPriceId()))
                .filter(existing -> existing.getStatus() == CommonStatus.active)
                .filter(existing -> "all".equals(existing.getDayType()) || "all".equals(candidate.getDayType())
                        || existing.getDayType().equals(candidate.getDayType()))
                .filter(existing -> candidate.getStartTime().isBefore(existing.getEndTime())
                        && candidate.getEndTime().isAfter(existing.getStartTime()))
                .anyMatch(existing -> {
                    LocalDate candidateFrom = candidate.getEffectiveFrom() == null ? LocalDate.MIN : candidate.getEffectiveFrom();
                    LocalDate candidateTo = candidate.getEffectiveTo() == null ? LocalDate.MAX : candidate.getEffectiveTo();
                    LocalDate existingFrom = existing.getEffectiveFrom() == null ? LocalDate.MIN : existing.getEffectiveFrom();
                    LocalDate existingTo = existing.getEffectiveTo() == null ? LocalDate.MAX : existing.getEffectiveTo();
                    return !candidateFrom.isAfter(existingTo) && !existingFrom.isAfter(candidateTo);
                });
        if (overlaps) throw support.badRequest("An active field price already overlaps this day, time, and effective-date range");
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
        List<Slot> candidates = support.slotRepository
                .findByField_FieldIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                        fieldId, today, today.plusDays(7));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> reservedSlotIds = support.bookingRepository
                .findBySlot_SlotIdInAndStatusIn(
                        candidates.stream().map(Slot::getSlotId).toList(),
                        DomainSupportService.ACTIVE_BOOKING_STATUSES)
                .stream()
                .map(booking -> booking.getSlot().getSlotId())
                .collect(java.util.stream.Collectors.toSet());
        List<FieldPrice> activePrices = support.fieldPriceRepository.findByField_FieldId(fieldId).stream()
                .filter(price -> price.getStatus() == CommonStatus.active)
                .toList();

        return candidates.stream()
                .filter(support::isSlotStartInFuture)
                .filter(slot -> slot.getStatus() == SlotStatus.available)
                .filter(slot -> !reservedSlotIds.contains(slot.getSlotId()))
                .flatMap(slot -> priceForSlot(activePrices, slot)
                        .map(price -> Map.<String, Object>ofEntries(
                                Map.entry("slotId", slot.getSlotId()),
                                Map.entry("slotDate", slot.getSlotDate().toString()),
                                Map.entry("startTime", slot.getStartTime().toString()),
                                Map.entry("endTime", slot.getEndTime().toString()),
                                Map.entry("price", price)
                        )).stream())
                .limit(5)
                .toList();
    }

    private Optional<BigDecimal> priceForSlot(List<FieldPrice> prices, Slot slot) {
        String dayType = slot.getSlotDate().getDayOfWeek() == DayOfWeek.SATURDAY
                || slot.getSlotDate().getDayOfWeek() == DayOfWeek.SUNDAY
                ? "weekend"
                : "weekday";
        return prices.stream()
                .filter(price -> price.getEffectiveFrom() == null || !slot.getSlotDate().isBefore(price.getEffectiveFrom()))
                .filter(price -> price.getEffectiveTo() == null || !slot.getSlotDate().isAfter(price.getEffectiveTo()))
                .filter(price -> price.getDayType().equalsIgnoreCase(dayType) || price.getDayType().equalsIgnoreCase("all"))
                .filter(price -> !slot.getStartTime().isBefore(price.getStartTime())
                        && !slot.getEndTime().isAfter(price.getEndTime()))
                .findFirst()
                .map(FieldPrice::getPrice);
    }
}
