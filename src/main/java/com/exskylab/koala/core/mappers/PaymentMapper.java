package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payment.response.PaymentAttemptDto;
import com.exskylab.koala.entities.Payment;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = PricingDtoMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PaymentMapper {

    @Mapping(target = "pricing", source = "payment")
    @Mapping(target = "workerPayout", expression = "java(payment.getWorkerPayout() == null ? null : payment.getWorkerPayout().toPlainString())")
    @Mapping(target = "commissionAmount", expression = "java(payment.getCommissionAmount() == null ? null : payment.getCommissionAmount().toPlainString())")
    @Mapping(target = "commissionRate", expression = "java(payment.getCommissionRate() == null ? null : payment.getCommissionRate().toPlainString())")
    @Mapping(target = "totalCharged", expression = "java(payment.getTotalCharged() == null ? null : payment.getTotalCharged().toPlainString())")
    PaymentAttemptDto toPaymentAttemptDto(Payment payment);

    List<PaymentAttemptDto> toPaymentAttemptDtoList(List<Payment> payments);
}
