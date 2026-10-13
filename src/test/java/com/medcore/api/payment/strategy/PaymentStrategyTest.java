package com.medcore.api.payment.strategy;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.integration.payment.PaymentGateway;
import com.medcore.api.integration.payment.PaymentGateway.PaymentIntentCommand;
import com.medcore.api.integration.payment.PaymentGateway.PaymentIntentResult;
import com.medcore.api.payment.Payment;
import com.medcore.api.payment.PaymentProvider;
import com.medcore.api.payment.PaymentStatus;
import com.medcore.api.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyTest {

    @Mock
    private PaymentGateway gateway;

    private PaymentStrategyResolver resolver;
    private Payment payment;

    @BeforeEach
    void setUp() {
        resolver = new PaymentStrategyResolver(List.of(
                new OnlineAppointmentPayment(gateway), new ClinicPayment(), new InsurancePayment()));
        Appointment appointment = TestFixtures.appointment(Instant.now().plus(Duration.ofDays(2)));
        payment = TestFixtures.withId(Payment.initiate(appointment, PaymentProvider.STRIPE, "EUR"));
    }

    @Test
    void resolvesOneStrategyPerProvider() {
        assertThat(resolver.resolve(PaymentProvider.STRIPE)).isInstanceOf(OnlineAppointmentPayment.class);
        assertThat(resolver.resolve(PaymentProvider.CLINIC)).isInstanceOf(ClinicPayment.class);
        assertThat(resolver.resolve(PaymentProvider.INSURANCE)).isInstanceOf(InsurancePayment.class);
    }

    @Test
    void rejectsDuplicateStrategies() {
        assertThatThrownBy(() -> new PaymentStrategyResolver(List.of(new ClinicPayment(), new ClinicPayment())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlinePaymentCreatesCustomerAndIdempotentPaymentIntent() {
        when(gateway.createCustomer(anyString(), anyString(), anyMap())).thenReturn("cus_123");
        when(gateway.createPaymentIntent(any())).thenReturn(new PaymentIntentResult("pi_123", "pi_123_secret", "requires_payment_method"));

        PaymentInitiation initiation = resolver.resolve(PaymentProvider.STRIPE).initiate(payment);

        ArgumentCaptor<PaymentIntentCommand> command = ArgumentCaptor.forClass(PaymentIntentCommand.class);
        verify(gateway).createPaymentIntent(command.capture());
        assertThat(command.getValue().customerId()).isEqualTo("cus_123");
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(command.getValue().currency()).isEqualTo("eur");
        assertThat(command.getValue().idempotencyKey()).isEqualTo("payment-" + payment.getId());

        assertThat(initiation.clientSecret()).isEqualTo("pi_123_secret");
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(payment.getPatient().getStripeCustomerId()).isEqualTo("cus_123");
    }

    @Test
    void onlinePaymentReusesExistingStripeCustomer() {
        payment.getPatient().setStripeCustomerId("cus_existing");
        when(gateway.createPaymentIntent(any())).thenReturn(new PaymentIntentResult("pi_1", "secret", "requires_payment_method"));

        resolver.resolve(PaymentProvider.STRIPE).initiate(payment);

        verify(gateway, never()).createCustomer(anyString(), anyString(), anyMap());
    }

    @Test
    void onlinePaymentCannotBeConfirmedManually() {
        assertThatThrownBy(() -> resolver.resolve(PaymentProvider.STRIPE).confirmManually(payment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("automatically");
    }

    @Test
    void clinicPaymentIsSettledByStaffWithoutGateway() {
        PaymentStrategy clinic = resolver.resolve(PaymentProvider.CLINIC);

        PaymentInitiation initiation = clinic.initiate(payment);
        assertThat(initiation.clientSecret()).isNull();
        assertThat(initiation.instructions()).contains("60.00 EUR");

        clinic.confirmManually(payment);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getPaidAt()).isNotNull();
        verifyNoInteractions(gateway);
    }

    @Test
    void insurancePaymentCreatesClaimReference() {
        resolver.resolve(PaymentProvider.INSURANCE).initiate(payment);
        assertThat(payment.getProviderPaymentId()).isEqualTo("CLAIM-" + payment.getId());
        verifyNoInteractions(gateway);
    }
}
