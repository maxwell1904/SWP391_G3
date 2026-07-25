package com.swp391.backend.service;

import com.swp391.backend.entity.FootballField;
import com.swp391.backend.entity.Slot;
import com.swp391.backend.enums.CommonStatus;
import com.swp391.backend.enums.SlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Materializes the rolling bookable calendar from administrator-owned rules.
 * Blocked slots and every slot referenced by a booking are preserved as exceptions.
 */
@Service
@Transactional
public class SlotGenerationService {
    public static final String OPENING_TIME_KEY = "slot.opening_time";
    public static final String CLOSING_TIME_KEY = "slot.closing_time";
    public static final String DURATION_MINUTES_KEY = "slot.duration_minutes";
    public static final String HORIZON_DAYS_KEY = "slot.generation_horizon_days";

    private static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(6, 0);
    private static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(22, 0);
    private static final int DEFAULT_DURATION_MINUTES = 120;
    private static final int DEFAULT_HORIZON_DAYS = 30;

    private final DomainSupportService support;

    public SlotGenerationService(DomainSupportService support) {
        this.support = support;
    }

    public GenerationResult generateRollingWindow() {
        SlotRules rules = rules();
        int created = 0;
        LocalDate today = LocalDate.now();
        for (int offset = 0; offset <= rules.horizonDays(); offset++) {
            created += generateForDate(today.plusDays(offset), rules);
        }
        return new GenerationResult(created, today, today.plusDays(rules.horizonDays()), rules);
    }

    public int ensureDate(LocalDate date) {
        if (date == null) {
            throw support.badRequest("Slot date is required");
        }
        SlotRules rules = rules();
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw support.badRequest("Slot date cannot be in the past");
        }
        if (date.isAfter(today.plusDays(rules.horizonDays()))) {
            throw support.badRequest("Bookings are available up to " + rules.horizonDays() + " days in advance");
        }
        return generateForDate(date, rules);
    }

    public SlotRules validateRuleChange(String key, String value) {
        Map<String, String> overrides = Map.of(key, value);
        return rules(overrides);
    }

    public GenerationResult rebuildRollingWindow() {
        SlotRules rules = rules();
        LocalDate today = LocalDate.now();
        LocalDate lastDate = today.plusDays(rules.horizonDays());
        List<Slot> removable = support.slotRepository
                .findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(today, lastDate).stream()
                .filter(slot -> slot.getCreatedBy() == null)
                .filter(slot -> slot.getStatus() == SlotStatus.available)
                .filter(slot -> !support.bookingRepository.existsBySlot(slot))
                .toList();
        support.slotRepository.deleteAll(removable);
        support.slotRepository.flush();
        return generateRollingWindow();
    }

    public SlotRules rules() {
        return rules(Map.of());
    }

    private SlotRules rules(Map<String, String> overrides) {
        LocalTime opening = parseTime(value(OPENING_TIME_KEY, overrides, DEFAULT_OPENING_TIME.toString()), "Opening time");
        LocalTime closing = parseTime(value(CLOSING_TIME_KEY, overrides, DEFAULT_CLOSING_TIME.toString()), "Closing time");
        int duration = parseInteger(value(DURATION_MINUTES_KEY, overrides, String.valueOf(DEFAULT_DURATION_MINUTES)),
                "Slot duration");
        int horizon = parseInteger(value(HORIZON_DAYS_KEY, overrides, String.valueOf(DEFAULT_HORIZON_DAYS)),
                "Generation horizon");
        if (!opening.isBefore(closing)) {
            throw support.badRequest("Opening time must be before closing time");
        }
        if (duration < 30 || duration > 360 || duration % 30 != 0) {
            throw support.badRequest("Slot duration must be between 30 and 360 minutes in 30-minute steps");
        }
        long operatingMinutes = Duration.between(opening, closing).toMinutes();
        if (operatingMinutes % duration != 0) {
            throw support.badRequest("Opening-to-closing time must divide exactly by the slot duration");
        }
        if (horizon < 1 || horizon > 90) {
            throw support.badRequest("Slot generation horizon must be between 1 and 90 days");
        }
        return new SlotRules(opening, closing, duration, horizon);
    }

    private synchronized int generateForDate(LocalDate date, SlotRules rules) {
        if (date.isEqual(LocalDate.now()) && !rules.closingTime().isAfter(LocalTime.now())) {
            return 0;
        }
        int created = 0;
        List<Slot> additions = new ArrayList<>();
        for (FootballField field : support.fieldRepository.findByStatus(CommonStatus.active)) {
            List<Slot> existing = new ArrayList<>(
                    support.slotRepository.findByField_FieldIdAndSlotDate(field.getFieldId(), date));
            int slotCount = (int) (Duration.between(rules.openingTime(), rules.closingTime()).toMinutes()
                    / rules.durationMinutes());
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                LocalTime start = rules.openingTime().plusMinutes((long) slotIndex * rules.durationMinutes());
                LocalTime end = start.plusMinutes(rules.durationMinutes());
                if (date.isEqual(LocalDate.now()) && !start.isAfter(LocalTime.now())) {
                    continue;
                }
                LocalTime candidateStart = start;
                boolean overlapsExisting = existing.stream().anyMatch(slot ->
                        candidateStart.isBefore(slot.getEndTime()) && end.isAfter(slot.getStartTime()));
                if (overlapsExisting) {
                    continue;
                }
                Slot slot = new Slot();
                slot.setField(field);
                slot.setSlotDate(date);
                slot.setStartTime(start);
                slot.setEndTime(end);
                slot.setStatus(SlotStatus.available);
                additions.add(slot);
                existing.add(slot);
                created++;
            }
        }
        if (!additions.isEmpty()) {
            support.slotRepository.saveAll(additions);
        }
        return created;
    }

    private String value(String key, Map<String, String> overrides, String fallback) {
        if (overrides.containsKey(key)) {
            return overrides.get(key);
        }
        return support.systemSettingRepository.findBySettingKey(key)
                .map(setting -> setting.getSettingValue())
                .orElse(fallback);
    }

    private LocalTime parseTime(String value, String label) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw support.badRequest(label + " must use HH:mm format");
        }
    }

    private int parseInteger(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw support.badRequest(label + " must be a whole number");
        }
    }

    public record SlotRules(
            LocalTime openingTime,
            LocalTime closingTime,
            int durationMinutes,
            int horizonDays
    ) {
    }

    public record GenerationResult(
            int createdSlots,
            LocalDate fromDate,
            LocalDate toDate,
            SlotRules rules
    ) {
    }
}
