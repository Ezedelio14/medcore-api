package com.medcore.api.appointment.dto;

import com.medcore.api.payment.dto.PaymentInitiationResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a booking: the appointment and how to pay for it")
public record BookingResponse(
        @Schema(description = "The newly created appointment") AppointmentResponse appointment,
        @Schema(description = "Payment instructions. Null if payment initiation failed; retry via POST /api/v1/payments.",
                nullable = true) PaymentInitiationResponse payment) {
}
