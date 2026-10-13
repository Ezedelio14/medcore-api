package com.medcore.api.integration.payment;

import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.common.exception.ExternalServiceException;
import com.medcore.api.config.MedcoreProperties;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/** Adapter: implements {@link PaymentGateway} on top of the Stripe Java SDK. */
@Slf4j
@Component
public class StripePaymentAdapter implements PaymentGateway {

    private final StripeClient stripe;
    private final String webhookSecret;

    public StripePaymentAdapter(MedcoreProperties properties) {
        this.stripe = new StripeClient(properties.stripe().apiKey());
        this.webhookSecret = properties.stripe().webhookSecret();
    }

    @Override
    public String createCustomer(String email, String fullName, Map<String, String> metadata) {
        try {
            Customer customer = stripe.v1().customers().create(CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(fullName)
                    .putAllMetadata(metadata)
                    .build());
            return customer.getId();
        } catch (StripeException e) {
            throw new ExternalServiceException("Stripe", "could not create customer", e);
        }
    }

    @Override
    public PaymentIntentResult createPaymentIntent(PaymentIntentCommand command) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(command.amount()))
                .setCurrency(command.currency())
                .setCustomer(command.customerId())
                .setDescription(command.description())
                .putAllMetadata(command.metadata())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true).build())
                .build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(command.idempotencyKey()).build();
        try {
            PaymentIntent intent = stripe.v1().paymentIntents().create(params, options);
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            throw new ExternalServiceException("Stripe", "could not create payment intent", e);
        }
    }

    @Override
    public RefundResult refund(String providerPaymentId, BigDecimal amount) {
        try {
            Refund refund = stripe.v1().refunds().create(RefundCreateParams.builder()
                    .setPaymentIntent(providerPaymentId)
                    .setAmount(toMinorUnits(amount))
                    .build());
            return new RefundResult(refund.getId(), refund.getStatus());
        } catch (StripeException e) {
            throw new ExternalServiceException("Stripe", "could not refund payment", e);
        }
    }

    @Override
    public Optional<GatewayEvent> parseWebhook(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Invalid Stripe webhook signature");
        }
        GatewayEvent.Type type = switch (event.getType()) {
            case "payment_intent.succeeded" -> GatewayEvent.Type.PAYMENT_SUCCEEDED;
            case "payment_intent.payment_failed" -> GatewayEvent.Type.PAYMENT_FAILED;
            case "payment_intent.canceled" -> GatewayEvent.Type.PAYMENT_CANCELED;
            case "charge.refunded" -> GatewayEvent.Type.CHARGE_REFUNDED;
            default -> null;
        };
        if (type == null) {
            log.debug("Ignoring Stripe event {} of type {}", event.getId(), event.getType());
            return Optional.empty();
        }
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_EVENT_VERSION",
                        "Stripe event API version does not match the SDK"));
        return Optional.of(switch (object) {
            case PaymentIntent intent -> new GatewayEvent(event.getId(), type, intent.getId(),
                    intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : null);
            case Charge charge -> new GatewayEvent(event.getId(), type, charge.getPaymentIntent(), null);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_EVENT", "Unsupported Stripe object");
        });
    }

    /** Stripe expects integer amounts in the currency's minor unit (cents). */
    static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
