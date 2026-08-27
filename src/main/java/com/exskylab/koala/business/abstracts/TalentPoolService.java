package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.talent.request.SaveTalentPoolEntryDto;
import com.exskylab.koala.entities.TalentPoolEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TalentPoolService {

    Page<TalentPoolEntry> getMyTalentPool(Pageable pageable);

    TalentPoolEntry addWorker(UUID workerId, SaveTalentPoolEntryDto request);

    void removeWorker(UUID workerId);
}
