package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payment.response.PaymentAttemptDto;
import com.exskylab.koala.entities.Payment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentAttemptDto toPaymentAttemptDto(Payment payment);

    List<PaymentAttemptDto> toPaymentAttemptDtoList(List<Payment> payments);
}
