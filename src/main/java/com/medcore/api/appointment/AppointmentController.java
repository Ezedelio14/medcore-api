package com.medcore.api.appointment;

import com.medcore.api.appointment.dto.AppointmentFilter;
import com.medcore.api.appointment.dto.AppointmentResponse;
import com.medcore.api.appointment.dto.AvailabilityResponse;
import com.medcore.api.appointment.dto.BookAppointmentRequest;
import com.medcore.api.appointment.dto.BookingResponse;
import com.medcore.api.appointment.dto.CancelAppointmentRequest;
import com.medcore.api.appointment.dto.RescheduleAppointmentRequest;
import com.medcore.api.common.web.PageResponse;
import com.medcore.api.config.OpenApiConfig;
import com.medcore.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Appointments", description = """
        Scheduling: doctor availability and free slots, booking with double-booking prevention,
        rescheduling, cancellation and the appointment lifecycle
        (PENDING → CONFIRMED → COMPLETED | NO_SHOW, or → CANCELLED).""")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying", schema = @Schema(type = "integer")),
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class AppointmentController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final AppointmentService appointmentService;
    private final AppointmentBookingFacade bookingFacade;
    private final AvailabilityService availabilityService;

    // ------------------------------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------------------------------

    @GetMapping("/api/v1/doctors/{doctorId}/availability")
    @Operation(
            operationId = "getDoctorAvailability",
            summary = "List a doctor's free slots",
            description = """
                    Returns bookable slots between `from` and `to` (inclusive, max 31 days), computed from the doctor's
                    weekly schedule, the clinic opening hours and existing appointments. Dates are interpreted in the
                    clinic time zone; slot instants are returned in UTC. **Public endpoint** (no token required).""",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Free slots",
                    content = @Content(schema = @Schema(implementation = AvailabilityResponse.class))),
            @ApiResponse(responseCode = "404", description = "Doctor not found",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Invalid date range",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AvailabilityResponse availability(
            @Parameter(description = "Doctor id", required = true, example = "8f14e45f-ceea-467a-9c3a-1f2d3c4b5a6e")
            @PathVariable UUID doctorId,
            @Parameter(description = "First day (ISO date, clinic time zone)", required = true, example = "2026-11-03")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (ISO date, inclusive). Defaults to `from`.", example = "2026-11-07")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : from;
        AvailabilityService.Availability availability = availabilityService.freeSlots(doctorId, from, end);
        var slots = availability.slots().stream()
                .map(s -> new AvailabilityResponse.SlotResponse(s.start(), s.end()))
                .toList();
        return new AvailabilityResponse(doctorId, from, end, availability.timezone(), slots);
    }

    // ------------------------------------------------------------------------------------------
    // Booking & queries
    // ------------------------------------------------------------------------------------------

    @PostMapping(value = "/api/v1/appointments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(
            operationId = "bookAppointment",
            summary = "Book an appointment",
            description = """
                    Books a free slot and starts the payment with the chosen strategy:

                    | paymentMethod | Behaviour |
                    |---|---|
                    | `STRIPE` (default) | Creates a Stripe PaymentIntent; confirm it client-side with the returned `clientSecret`. The appointment is auto-confirmed when Stripe reports success. |
                    | `CLINIC` | Pay at the front desk; staff confirm the payment. |
                    | `INSURANCE` | Creates an insurance claim reference; staff confirm after authorization. |

                    Patients always book for themselves. Clinic staff must send `patientId`.
                    Double booking is impossible: concurrent requests for the same slot get `409 SLOT_UNAVAILABLE`.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = BookAppointmentRequest.class),
                            examples = @ExampleObject(name = "Online payment", value = """
                                    {
                                      "doctorId": "8f14e45f-ceea-467a-9c3a-1f2d3c4b5a6e",
                                      "scheduledAt": "2026-11-03T09:30:00Z",
                                      "reason": "Recurring headaches for two weeks",
                                      "paymentMethod": "STRIPE"
                                    }"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment booked",
                    headers = @Header(name = "Location", description = "URL of the new appointment"),
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g. scheduledAt in the past)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.medcore.health/errors/validation-error",
                                      "title": "Bad Request",
                                      "status": 400,
                                      "detail": "Request validation failed",
                                      "code": "VALIDATION_ERROR",
                                      "errors": { "scheduledAt": "must be a future date" }
                                    }"""))),
            @ApiResponse(responseCode = "403", description = "Caller may not book at this clinic",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Doctor or patient not found",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Slot already taken (`SLOT_UNAVAILABLE`)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Not a valid slot, doctor unavailable or patient profile missing",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        BookingResponse response = bookingFacade.book(CurrentUser.get(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/appointments/{id}").buildAndExpand(response.appointment().id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/v1/appointments")
    @Operation(
            operationId = "searchAppointments",
            summary = "Search appointments",
            description = """
                    Paginated search with optional filters (combined with AND). Results are automatically scoped to
                    what the caller may see: patients see their own, doctors their agenda, staff their clinics.
                    Sort by any field, e.g. `sort=scheduledAt,asc`.""")
    @ApiResponse(responseCode = "200", description = "Page of appointments")
    public PageResponse<AppointmentResponse> search(
            @ParameterObject AppointmentFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(appointmentService.search(CurrentUser.get(), filter, pageable), AppointmentResponse::from);
    }

    @GetMapping("/api/v1/doctors/{doctorId}/appointments")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(operationId = "getDoctorAgenda", summary = "Doctor agenda",
            description = "Appointments of a doctor in a time window. Doctors can only read their own agenda.")
    @ApiResponse(responseCode = "200", description = "Page of appointments")
    public PageResponse<AppointmentResponse> doctorAgenda(
            @Parameter(description = "Doctor id", required = true) @PathVariable UUID doctorId,
            @Parameter(description = "Window start (inclusive)", example = "2026-11-03T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Window end (exclusive)", example = "2026-11-10T00:00:00Z")
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50, sort = "scheduledAt") Pageable pageable) {
        AppointmentFilter filter = new AppointmentFilter(doctorId, null, null, null, null, from, to);
        return PageResponse.of(appointmentService.search(CurrentUser.get(), filter, pageable), AppointmentResponse::from);
    }

    @GetMapping("/api/v1/appointments/{id}")
    @Operation(operationId = "getAppointment", summary = "Get an appointment by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment found",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not your appointment",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse get(
            @Parameter(description = "Appointment id", required = true, in = ParameterIn.PATH) @PathVariable UUID id) {
        return AppointmentResponse.from(appointmentService.get(CurrentUser.get(), id));
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle transitions (State pattern)
    // ------------------------------------------------------------------------------------------

    @PostMapping(value = "/api/v1/appointments/{id}/reschedule", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "rescheduleAppointment", summary = "Reschedule an appointment",
            description = """
                    Moves a PENDING or CONFIRMED appointment to another free slot of the same doctor.
                    A confirmed appointment returns to PENDING. Patients must reschedule at least
                    `medcore.appointments.cancellation-min-notice` (default 2h) in advance.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rescheduled",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "409", description = "Slot taken or invalid state transition",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Not a valid slot or insufficient notice",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse reschedule(@Parameter(description = "Appointment id") @PathVariable UUID id,
                                          @Valid @RequestBody RescheduleAppointmentRequest request) {
        return AppointmentResponse.from(appointmentService.reschedule(CurrentUser.get(), id, request.scheduledAt()));
    }

    @PostMapping("/api/v1/appointments/{id}/confirm")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(operationId = "confirmAppointment", summary = "Confirm an appointment",
            description = "PENDING → CONFIRMED. Emits `appointment.confirmed`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmed",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition (`INVALID_STATE_TRANSITION`)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse confirm(@Parameter(description = "Appointment id") @PathVariable UUID id) {
        return AppointmentResponse.from(appointmentService.confirm(CurrentUser.get(), id));
    }

    @PostMapping(value = "/api/v1/appointments/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "cancelAppointment", summary = "Cancel an appointment",
            description = """
                    PENDING | CONFIRMED → CANCELLED, freeing the slot. If the appointment was paid, the payment is
                    refunded automatically through its payment strategy. Emits `appointment.cancelled`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient notice (patients)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse cancel(@Parameter(description = "Appointment id") @PathVariable UUID id,
                                      @Valid @RequestBody CancelAppointmentRequest request) {
        return bookingFacade.cancel(CurrentUser.get(), id, request.reason());
    }

    @PostMapping("/api/v1/appointments/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(operationId = "completeAppointment", summary = "Mark an appointment as completed",
            description = "CONFIRMED → COMPLETED, only once the appointment has started. Emits `appointment.completed`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse complete(@Parameter(description = "Appointment id") @PathVariable UUID id) {
        return AppointmentResponse.from(appointmentService.complete(CurrentUser.get(), id));
    }

    @PostMapping("/api/v1/appointments/{id}/no-show")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')")
    @Operation(operationId = "markAppointmentNoShow", summary = "Mark the patient as no-show",
            description = "CONFIRMED → NO_SHOW, only once the appointment has started.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked as no-show",
                    content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AppointmentResponse noShow(@Parameter(description = "Appointment id") @PathVariable UUID id) {
        return AppointmentResponse.from(appointmentService.markNoShow(CurrentUser.get(), id));
    }
}
