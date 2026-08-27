package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountDao extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByUserIdAndPrimaryAccountTrueAndActiveTrue(UUID userId);

    List<BankAccount> findAllByUserIdAndActiveTrue(UUID userId);
}
