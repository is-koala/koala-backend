package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TalentPoolService;
import com.exskylab.koala.core.constants.TalentPoolMessages;
import com.exskylab.koala.core.dtos.talent.request.SaveTalentPoolEntryDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.TalentPoolEntryDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.TalentPoolEntry;
import com.exskylab.koala.entities.User;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TalentPoolManager implements TalentPoolService {

    private final Logger logger = LoggerFactory.getLogger(TalentPoolManager.class);

    private final TalentPoolEntryDao talentPoolEntryDao;
    private final UserDao userDao;
    private final CompanyService companyService;
    private final SecurityService securityService;

    public TalentPoolManager(TalentPoolEntryDao talentPoolEntryDao, UserDao userDao,
                             CompanyService companyService, SecurityService securityService) {
        this.talentPoolEntryDao = talentPoolEntryDao;
        this.userDao = userDao;
        this.companyService = companyService;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TalentPoolEntry> getMyTalentPool(Pageable pageable) {
        return talentPoolEntryDao.findAllByEmployerId(securityService.getAuthenticatedUserId(), pageable);
    }

    @Override
    @Transactional
    public TalentPoolEntry addWorker(UUID workerId, SaveTalentPoolEntryDto request) {
        User employer = securityService.getAuthenticatedUser();

        if (employer.getId().equals(workerId)) {
            throw new ValidationException(TalentPoolMessages.CANNOT_ADD_SELF);
        }

        User worker = userDao.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException(TalentPoolMessages.WORKER_NOT_FOUND));

        TalentPoolEntry entry = talentPoolEntryDao
                .findByEmployerIdAndWorkerId(employer.getId(), workerId)
                .orElseGet(() -> {
                    TalentPoolEntry created = new TalentPoolEntry();
                    created.setEmployer(employer);
                    created.setWorker(worker);
                    return created;
                });

        entry.setNote(request == null ? null : request.getNote());
        if (request != null && request.getCompanyId() != null) {
            entry.setCompany(companyService.getCompanyById(request.getCompanyId()));
        }

        TalentPoolEntry saved = talentPoolEntryDao.save(entry);
        logger.info("Worker {} added to the talent pool of employer {}", workerId, employer.getId());
        return saved;
    }

    @Override
    @Transactional
    public void removeWorker(UUID workerId) {
        talentPoolEntryDao
                .findByEmployerIdAndWorkerId(securityService.getAuthenticatedUserId(), workerId)
                .ifPresent(entry -> {
                    talentPoolEntryDao.delete(entry);
                    logger.info("Worker {} removed from the talent pool", workerId);
                });
    }
}
