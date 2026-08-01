package com.swp391.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

/** Keeps every LocalDate/LocalDateTime business rule on the venue clock. */
@Configuration
public class VenueTimeZoneConfig {
    public VenueTimeZoneConfig(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String configuredZone) {
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(configuredZone)));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid app.time-zone: " + configuredZone, exception);
        }
    }
}
