package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.messaging.response.ConversationDto;
import com.exskylab.koala.core.dtos.messaging.response.ConversationParticipantDto;
import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.core.dtos.messaging.response.UserBlockDto;
import com.exskylab.koala.core.helpers.DisplayNames;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.dataAccess.ConversationDao.ParticipantView;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.UserBlock;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class, MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MessagingMapper {

    @Mapping(target = "id", source = "message.id")
    @Mapping(target = "senderId", source = "message.sender.id")
    @Mapping(target = "senderName", source = "sender", qualifiedByName = "displayName")
    @Mapping(target = "senderProfilePictureUrl", source = "sender.profilePictureFileUrl",
            qualifiedByName = "fileUrlToPublicUrl")
    @Mapping(target = "body", source = "message.body")
    @Mapping(target = "sentAt", source = "message.sentAt")
    @Mapping(target = "readAt", source = "message.readAt")
    MessageDto toMessageDto(Message message, ParticipantView sender);

    @Mapping(target = "jobId", source = "conversation.job.id")
    @Mapping(target = "jobTitle", source = "conversation.job.title")
    @Mapping(target = "unreadCount", source = "unreadCount")
    @Mapping(target = "closed", source = "closed")
    @Mapping(target = "participants", source = "members")
    ConversationDto toConversationDto(Conversation conversation, long unreadCount, boolean closed,
                                      List<ConversationParticipantDto> members);

    @Mapping(target = "id", source = "userId")
    @Mapping(target = "profilePictureUrl", source = "profilePictureFileUrl", qualifiedByName = "fileUrlToPublicUrl")
    @Mapping(target = "left", source = "leftConversation")
    ConversationParticipantDto toParticipantDto(ParticipantView row);

    UserBlockDto toUserBlockDto(UserBlock block);

    List<UserBlockDto> toUserBlockDtoList(List<UserBlock> blocks);

    @Named("displayName")
    default String displayName(ParticipantView participant) {
        return participant == null ? null : DisplayNames.of(participant.getFirstName(), participant.getLastName());
    }
}
