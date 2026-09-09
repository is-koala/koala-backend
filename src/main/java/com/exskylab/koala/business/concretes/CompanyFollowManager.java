package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyFollowService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.properties.CompanyFollowProperties;
import com.exskylab.koala.dataAccess.CompanyFollowDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyFollow;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CompanyFollowManager implements CompanyFollowService {

    private static final Set<String> SORTABLE = Set.of("createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");


    private final PageableSanitizer pageableSanitizer;
    private final CompanyFollowDao companyFollowDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final MediaUrlResolver mediaUrlResolver;
    private final CompanyFollowProperties properties;

    public CompanyFollowManager(CompanyFollowDao companyFollowDao, UserService userService,
                                NotificationService notificationService,
                                MediaUrlResolver mediaUrlResolver, CompanyFollowProperties properties,
                                PageableSanitizer pageableSanitizer) {
        this.companyFollowDao = companyFollowDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.mediaUrlResolver = mediaUrlResolver;
        this.properties = properties;
            this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public void follow(Company company, UUID followerId) {
        if (companyFollowDao.existsByCompanyIdAndFollowerId(company.getId(), followerId)) {
            log.debug("User {} already follows company {}", followerId, company.getId());
            return;
        }

        CompanyFollow entry = new CompanyFollow();
        entry.setCompany(company);
        entry.setFollower(userService.getReference(followerId));

        companyFollowDao.save(entry);
        log.info("User {} started following company {}", followerId, company.getId());
    }

    @Override
    @Transactional
    public void unfollow(UUID companyId, UUID followerId) {
        companyFollowDao.findByCompanyIdAndFollowerId(companyId, followerId)
                .ifPresent(entry -> {
                    companyFollowDao.delete(entry);
                    log.info("User {} stopped following company {}", followerId, companyId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(UUID companyId, UUID followerId) {
        return followerId != null && companyFollowDao.existsByCompanyIdAndFollowerId(companyId, followerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long followerCount(UUID companyId) {
        return companyFollowDao.countByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<FollowedCompanyDto> getFollowedCompanies(UUID followerId, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);
        Pageable pageable = pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT);

        log.debug("Listing companies followed by user {}", followerId);

        return PageDto.of(companyFollowDao.findAllByFollowerId(followerId, pageable), entry -> {
            Company company = entry.getCompany();
            return new FollowedCompanyDto(company.getId(), company.getName(),
                    mediaUrlResolver.toPublicUrl(company.getLogo()), company.getDescription(),
                    companyFollowDao.countByCompanyId(company.getId()), entry.getCreatedAt());
        });
    }

    @Override
    @Transactional
    public void notifyFollowers(Job job) {
        if (!properties.isEnabled() || job.getCompany() == null) {
            return;
        }

        UUID creatorId = job.getCreator() == null ? null : job.getCreator().getId();
        List<CompanyFollow> followers = companyFollowDao.findAllByCompanyId(job.getCompany().getId()).stream()
                .filter(follow -> !follow.getFollower().getId().equals(creatorId))
                .toList();
        notificationService.preparePushRecipients(followers.stream()
                .map(follow -> follow.getFollower().getId())
                .toList());

        for (CompanyFollow follow : followers) {
            User follower = follow.getFollower();

            try {
                SendPushToUserDto push = new SendPushToUserDto();
                push.setRecipient(follower);
                push.setTitle(job.getCompany().getName());
                push.setBody(job.getTitle());
                push.setCategory(NotificationCategory.JOB_ALERTS);

                notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);
            } catch (Exception exception) {
                log.warn("Follower {} could not be notified about job {}: {}",
                        follower.getId(), job.getId(), exception.getMessage());
            }
        }
    }
}
