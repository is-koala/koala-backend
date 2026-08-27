package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.messaging.response.ConversationDto;
import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.Message;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MessagingMapper {

    @Mapping(target = "senderId", source = "sender.id")
    MessageDto toMessageDto(Message message);

    List<MessageDto> toMessageDtoList(List<Message> messages);

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "jobTitle", source = "job.title")
    ConversationDto toConversationDto(Conversation conversation);

    List<ConversationDto> toConversationDtoList(List<Conversation> conversations);
}
