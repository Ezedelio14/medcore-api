package com.medcore.api.payment.strategy;

import com.medcore.api.common.exception.BusinessException;
import com.medcore.api.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Picks the {@link PaymentStrategy} for a provider. New strategies are discovered automatically as beans. */
@Component
public class PaymentStrategyResolver {

    private final Map<PaymentProvider, PaymentStrategy> strategies = new EnumMap<>(PaymentProvider.class);

    public PaymentStrategyResolver(List<PaymentStrategy> available) {
        available.forEach(strategy -> {
            if (strategies.putIfAbsent(strategy.provider(), strategy) != null) {
                throw new IllegalStateException("Duplicate payment strategy for " + strategy.provider());
            }
        });
    }

    public PaymentStrategy resolve(PaymentProvider provider) {
        PaymentStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new BusinessException("PAYMENT_METHOD_UNSUPPORTED", "Payment method %s is not supported".formatted(provider));
        }
        return strategy;
    }
}
