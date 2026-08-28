package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Job;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CompanyFollowService {

    void follow(Company company, UUID followerId);

    void unfollow(UUID companyId, UUID followerId);

    boolean isFollowing(UUID companyId, UUID followerId);

    long followerCount(UUID companyId);

    PageDto<FollowedCompanyDto> getFollowedCompanies(UUID followerId, Pageable pageable);

    void notifyFollowers(Job job);
}
