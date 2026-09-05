package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payment.response.PaymentAttemptDto;
import com.exskylab.koala.entities.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = PricingDtoMapper.class)
public interface PaymentMapper {

    @Mapping(target = "pricing", source = "payment")
    PaymentAttemptDto toPaymentAttemptDto(Payment payment);

    List<PaymentAttemptDto> toPaymentAttemptDtoList(List<Payment> payments);
}
