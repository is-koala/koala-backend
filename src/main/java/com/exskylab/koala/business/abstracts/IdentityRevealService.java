package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.identity.response.AdminIdentityRevealDto;
import com.exskylab.koala.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IdentityRevealService {

    User revealWorkerIdentity(UUID assignmentId, String clientIpAddress);

    Page<AdminIdentityRevealDto> listForStaff(UUID workerId, Pageable pageable);
}
