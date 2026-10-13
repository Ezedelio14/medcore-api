package com.medcore.api;

import com.medcore.api.clinic.Clinic;
import com.medcore.api.clinic.ClinicRepository;
import com.medcore.api.doctor.Doctor;
import com.medcore.api.doctor.DoctorRepository;
import com.medcore.api.doctor.Schedule;
import com.medcore.api.doctor.ScheduleRepository;
import com.medcore.api.integration.email.EmailSender;
import com.medcore.api.integration.payment.PaymentGateway;
import com.medcore.api.integration.payment.PaymentGateway.GatewayEvent;
import com.medcore.api.integration.payment.PaymentGateway.PaymentIntentResult;
import com.medcore.api.user.Role;
import com.medcore.api.user.User;
import com.medcore.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test against real PostgreSQL, Redis and Kafka (Testcontainers). Skipped when Docker is unavailable.
 * External providers (SMTP, Stripe) are mocked at their port interfaces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MedcoreApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository userRepository;
    @Autowired ClinicRepository clinicRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired ScheduleRepository scheduleRepository;

    @MockitoBean EmailSender emailSender;
    @MockitoBean PaymentGateway paymentGateway;

    private Doctor doctor;

    @BeforeEach
    void seedDoctorWorkingEveryDay() {
        Clinic clinic = new Clinic();
        clinic.setName("MedCore Test Clinic");
        clinic.setTimezone("UTC");
        clinic = clinicRepository.save(clinic);

        User doctorUser = userRepository.save(new User("dr-" + UUID.randomUUID() + "@medcore.test", "{noop}x", Role.DOCTOR));
        doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setClinic(clinic);
        doctor.setFirstName("Rita");
        doctor.setLastName("Costa");
        doctor.setLicenseNumber("LIC-" + UUID.randomUUID());
        doctor.setConsultationPrice(new BigDecimal("60.00"));
        doctor = doctorRepository.save(doctor);

        for (DayOfWeek day : DayOfWeek.values()) {
            Schedule schedule = new Schedule();
            schedule.setDoctor(doctor);
            schedule.setDayOfWeek(day);
            schedule.setStartTime(LocalTime.of(8, 0));
            schedule.setEndTime(LocalTime.of(18, 0));
            schedule.setAppointmentDuration(30);
            scheduleRepository.save(schedule);
        }
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MedCore Healthcare Platform API"))
                .andExpect(jsonPath("$.paths['/api/v1/appointments'].post.operationId").value("bookAppointment"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/appointments").header("X-Correlation-Id", "test-correlation-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-123"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void patientRegistersBooksAndPaysOnline() throws Exception {
        String accessToken = registerVerifyAndLogin("ana-" + UUID.randomUUID() + "@medcore.test");

        // Availability (public)
        LocalDate day = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        JsonNode availability = body(mvc.perform(get("/api/v1/doctors/{id}/availability", doctor.getId()).param("from", day.toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(availability.get("slots").size()).isEqualTo(20); // 08:00-18:00 in 30 min slots
        String slot = availability.get("slots").get(0).get("start").asString();

        // Book with front-desk payment
        JsonNode booking = body(mvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("doctorId", doctor.getId(), "scheduledAt", slot,
                                "reason", "Annual check-up", "paymentMethod", "CLINIC"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.appointment.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.provider").value("CLINIC"))
                .andReturn());
        assertThat(booking.get("appointment").get("price").decimalValue()).isEqualByComparingTo("60.00");

        // Same slot again -> double booking prevented
        mvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("doctorId", doctor.getId(), "scheduledAt", slot))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        // The booked slot disappears from availability
        mvc.perform(get("/api/v1/doctors/{id}/availability", doctor.getId()).param("from", day.toString()))
                .andExpect(jsonPath("$.slots.length()").value(19));

        // Online (Stripe) booking, settled through the webhook
        when(paymentGateway.createCustomer(anyString(), anyString(), anyMap())).thenReturn("cus_test");
        when(paymentGateway.createPaymentIntent(any())).thenReturn(new PaymentIntentResult("pi_test_1", "pi_test_1_secret", "requires_payment_method"));
        String secondSlot = availability.get("slots").get(1).get("start").asString();
        JsonNode online = body(mvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("doctorId", doctor.getId(), "scheduledAt", secondSlot))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.clientSecret").value("pi_test_1_secret"))
                .andExpect(jsonPath("$.payment.status").value("REQUIRES_ACTION"))
                .andReturn());
        String appointmentId = online.get("appointment").get("id").asString();

        when(paymentGateway.parseWebhook(eq("{}"), eq("sig")))
                .thenReturn(Optional.of(new GatewayEvent("evt_1", GatewayEvent.Type.PAYMENT_SUCCEEDED, "pi_test_1", null)));
        mvc.perform(post("/api/v1/webhooks/stripe").header("Stripe-Signature", "sig").content("{}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/appointments/{id}", appointmentId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Search is scoped to the patient's own appointments
        mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private String registerVerifyAndLogin(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Str0ng-Passw0rd",
                                "firstName", "Ana", "lastName", "Silva"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        ArgumentCaptor<String> mailBody = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).send(eq(email), anyString(), mailBody.capture());
        Matcher token = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailBody.getValue());
        assertThat(token.find()).isTrue();

        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token.group(1)))))
                .andExpect(status().isNoContent());

        JsonNode login = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Str0ng-Passw0rd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andReturn());
        return login.get("tokens").get("accessToken").asString();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
