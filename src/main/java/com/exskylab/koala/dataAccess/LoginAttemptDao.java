package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LoginAttemptDao extends JpaRepository<LoginAttempt, UUID> {

    @Modifying
    @Query(value = "delete from login_attempts "
            + "where user_id is null and coalesce(time_stamp, created_at) < :cutoff",
            nativeQuery = true)
    int deleteUnattributedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = "delete from login_attempts "
            + "where user_id is not null and coalesce(time_stamp, created_at) < :cutoff",
            nativeQuery = true)
    int deleteAttributedBefore(@Param("cutoff") Instant cutoff);
}
