package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AgreementType;
import com.exskylab.koala.entities.AgreementVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementVersionDao extends JpaRepository<AgreementVersion, UUID> {

    @Query("select a from AgreementVersion a where a.retiredAt is null order by a.type")
    List<AgreementVersion> findAllCurrent();

    Optional<AgreementVersion> findFirstByTypeAndLocaleAndRetiredAtIsNullOrderByEffectiveFromDesc(
            AgreementType type, String locale);
}
