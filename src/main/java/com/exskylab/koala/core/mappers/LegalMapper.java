package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.legal.response.AgreementVersionDto;
import com.exskylab.koala.core.dtos.legal.response.ConsentRecordDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.dtos.legal.response.DeletionBlockerDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AgreementVersion;
import com.exskylab.koala.entities.ConsentRecord;
import org.mapstruct.Mapper;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
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

    @Named("participantView")
    @Mapping(target = "blockedReason",
            expression = "java(messageResolver.resolve(request.getBlockedReason()))")
    @Mapping(target = "rejectedByStaffId", ignore = true)
    @Mapping(target = "blockers", ignore = true)
    AccountDeletionDto toAccountDeletionDto(AccountDeletionRequest request,
                                             @Context MessageResolver messageResolver);

    @IterableMapping(qualifiedByName = "participantView")
    List<AccountDeletionDto> toAccountDeletionDtoList(List<AccountDeletionRequest> requests,
                                                       @Context MessageResolver messageResolver);

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subjectId", source = "request.subjectId")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "requestedAt", source = "request.requestedAt")
    @Mapping(target = "processedAt", source = "request.processedAt")
    @Mapping(target = "blockedReason",
            expression = "java(messageResolver.resolve(request.getBlockedReason()))")
    @Mapping(target = "rejectionReason", source = "request.rejectionReason")
    @Mapping(target = "rejectedByStaffId", source = "request.rejectedByStaffId")
    @Mapping(target = "blockers", source = "blockers")
    AccountDeletionDto toStaffAccountDeletionDto(AccountDeletionRequest request, List<DeletionBlockerDto> blockers,
                                                  @Context MessageResolver messageResolver);
}
