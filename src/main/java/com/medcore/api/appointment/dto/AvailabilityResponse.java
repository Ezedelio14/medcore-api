package com.medcore.api.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Free slots of a doctor in a date range")
public record AvailabilityResponse(
        @Schema(description = "Doctor id") UUID doctorId,
        @Schema(description = "First day (inclusive)", example = "2026-11-03") LocalDate from,
        @Schema(description = "Last day (inclusive)", example = "2026-11-07") LocalDate to,
        @Schema(description = "Clinic time zone used to interpret the dates", example = "Europe/Lisbon") String timezone,
        @Schema(description = "Bookable slots, chronologically ordered") List<SlotResponse> slots) {

    @Schema(description = "A bookable time slot")
    public record SlotResponse(
            @Schema(example = "2026-11-03T09:30:00Z") Instant start,
            @Schema(example = "2026-11-03T10:00:00Z") Instant end) {
    }
}
