package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PublicProfileService;
import com.exskylab.koala.business.abstracts.RatingSummaryService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.user.response.PublicProfileDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.RatingFigures;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class PublicProfileManager implements PublicProfileService {

    private final UserService userService;
    private final JobApplicationService jobApplicationService;
    private final SecurityService securityService;
    private final MediaUrlResolver mediaUrlResolver;
    private final JobAssignmentService jobAssignmentService;
    private final RatingSummaryService ratingSummaryService;
    private final BadgeService badgeService;
    private final BadgeMapper badgeMapper;

    public PublicProfileManager(UserService userService, JobApplicationService jobApplicationService,
                                SecurityService securityService, MediaUrlResolver mediaUrlResolver,
                                JobAssignmentService jobAssignmentService, RatingSummaryService ratingSummaryService,
                                BadgeService badgeService, BadgeMapper badgeMapper) {
        this.userService = userService;
        this.jobApplicationService = jobApplicationService;
        this.securityService = securityService;
        this.mediaUrlResolver = mediaUrlResolver;
        this.jobAssignmentService = jobAssignmentService;
        this.ratingSummaryService = ratingSummaryService;
        this.badgeService = badgeService;
        this.badgeMapper = badgeMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfile(UUID userId) {
        log.debug("Building public profile for user {}", userId);

        User user = userService.getById(userId);
        Address address = user.getAddress();
        RatingFigures rating = ratingSummaryService.figuresOf(userId);

        return new PublicProfileDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                mediaUrlResolver.toPublicUrl(user.getProfilePicture()),
                user.getJobTitle(),
                user.getAbout(),
                address == null || address.getCity() == null ? null : address.getCity().getName(),
                address == null || address.getDistrict() == null ? null : address.getDistrict().getName(),
                visibleCvUrl(user),
                jobAssignmentService.countCompletedForWorker(userId),
                rating.averageRating(),
                rating.reviewCount(),
                badgeMapper.toBadgeDtoList(badgeService.getBadgesOf(userId)));
    }

    private String visibleCvUrl(User user) {
        if (user.getCv() == null) {
            return null;
        }

        UUID viewerId = securityService.getAuthenticatedUserId();
        if (viewerId.equals(user.getId())
                || jobApplicationService.hasApplicationForEmployer(viewerId, user.getId())) {
            return mediaUrlResolver.toFileUrl(user.getCv());
        }

        return null;
    }
}
