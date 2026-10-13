package com.medcore.api.payment;

import com.medcore.api.config.OpenApiConfig;
import com.medcore.api.payment.dto.CreatePaymentRequest;
import com.medcore.api.payment.dto.PaymentInitiationResponse;
import com.medcore.api.payment.dto.PaymentResponse;
import com.medcore.api.payment.dto.ReceiptResponse;
import com.medcore.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment strategies (STRIPE, CLINIC, INSURANCE), refunds and receipts")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a payment for an appointment",
            description = "Used to retry a failed payment or switch method. STRIPE returns a PaymentIntent client secret.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment initiated"),
            @ApiResponse(responseCode = "409", description = "Appointment already paid",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Payment provider error",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PaymentInitiationResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.initiate(CurrentUser.get(), request.appointmentId(), request.method());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment status")
    @ApiResponse(responseCode = "200", description = "Payment")
    public PaymentResponse get(@Parameter(description = "Payment id") @PathVariable UUID id) {
        return PaymentResponse.from(paymentService.get(CurrentUser.get(), id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Confirm a front-desk or insurance payment", description = "Not available for STRIPE payments (settled by webhook).")
    @ApiResponse(responseCode = "200", description = "Payment settled")
    public PaymentResponse confirm(@Parameter(description = "Payment id") @PathVariable UUID id) {
        return PaymentResponse.from(paymentService.confirmManually(CurrentUser.get(), id));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Refund a payment", description = "Full refund through the payment's strategy. Emits `payment.refunded`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refunded"),
            @ApiResponse(responseCode = "409", description = "Payment not refundable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PaymentResponse refund(@Parameter(description = "Payment id") @PathVariable UUID id) {
        return PaymentResponse.from(paymentService.refund(CurrentUser.get(), id));
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Get the receipt of a completed payment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt"),
            @ApiResponse(responseCode = "422", description = "Payment not completed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ReceiptResponse receipt(@Parameter(description = "Payment id") @PathVariable UUID id) {
        return paymentService.receipt(CurrentUser.get(), id);
    }
}
