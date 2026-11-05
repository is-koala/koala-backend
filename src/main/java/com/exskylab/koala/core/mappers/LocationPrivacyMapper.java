package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminTimelineEventDto;
import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface LocationPrivacyMapper {

    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "locationWithheld",
            expression = "java(event.latitude() != null || event.longitude() != null)")
    AdminTimelineEventDto withoutLocation(AdminTimelineEventDto event);

    @Mapping(target = "checkInLatitude", ignore = true)
    @Mapping(target = "checkInLongitude", ignore = true)
    @Mapping(target = "checkOutLatitude", ignore = true)
    @Mapping(target = "checkOutLongitude", ignore = true)
    @Mapping(target = "checkInLocationWithheld",
            expression = "java(session.checkInLatitude() != null || session.checkInLongitude() != null)")
    @Mapping(target = "checkOutLocationWithheld",
            expression = "java(session.checkOutLatitude() != null || session.checkOutLongitude() != null)")
    WorkSessionDto withoutLocation(WorkSessionDto session);
}
