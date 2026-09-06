package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.legal.response.AgreementVersionDto;
import com.exskylab.koala.core.dtos.legal.response.ConsentRecordDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AgreementVersion;
import com.exskylab.koala.entities.ConsentRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Context;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LegalMapper {

    AgreementVersionDto toAgreementVersionDto(AgreementVersion agreementVersion);

    List<AgreementVersionDto> toAgreementVersionDtoList(List<AgreementVersion> agreementVersions);

    @Mapping(target = "type", source = "agreementVersion.type")
    @Mapping(target = "version", source = "agreementVersion.version")
    ConsentRecordDto toConsentRecordDto(ConsentRecord consentRecord);

    List<ConsentRecordDto> toConsentRecordDtoList(List<ConsentRecord> consentRecords);

    @Mapping(target = "blockedReason",
            expression = "java(messageResolver.resolve(request.getBlockedReason()))")
    AccountDeletionDto toAccountDeletionDto(AccountDeletionRequest request,
                                             @Context MessageResolver messageResolver);

    List<AccountDeletionDto> toAccountDeletionDtoList(List<AccountDeletionRequest> requests,
                                                       @Context MessageResolver messageResolver);
}
