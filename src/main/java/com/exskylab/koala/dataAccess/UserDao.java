package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDao extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByTcIdentityNumber(String tcIdentityNumber);

    List<User> findByEmailVerifiedTrue();

    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);

    @Query("""
            select u from User u
            where cast(:query as string) is null
               or lower(u.email) like lower(concat('%', cast(:query as string), '%'))
               or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', cast(:query as string), '%'))
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);
}
