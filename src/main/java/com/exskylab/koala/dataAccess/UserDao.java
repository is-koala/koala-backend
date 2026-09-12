package com.exskylab.koala.dataAccess;

import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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

    @Query(value = """
            select a.role as "role",
                   a.granted_at as "grantedAt",
                   a.granted_by_id as "grantedById",
                   nullif(trim(coalesce(g.first_name, '') || ' ' || coalesce(g.last_name, '')), '') as "grantedByName"
            from authorities a
            left join users g on g.id = a.granted_by_id
            where a.user_id = :userId
            order by a.role
            """, nativeQuery = true)
    List<RoleGrantView> findRoleGrants(@Param("userId") UUID userId);

    interface RoleGrantView {
        String getRole();

        Instant getGrantedAt();

        UUID getGrantedById();

        String getGrantedByName();
    }

    @Modifying(flushAutomatically = true)
    @Query(value = "update authorities set granted_at = :grantedAt, granted_by_id = :grantedById "
            + "where user_id = :userId and role = :role", nativeQuery = true)
    int stampRoleGrant(@Param("userId") UUID userId, @Param("role") String role,
                       @Param("grantedAt") Instant grantedAt, @Param("grantedById") UUID grantedById);

    @Modifying(flushAutomatically = true)
    @Query(value = "update authorities set granted_at = :grantedAt "
            + "where user_id = :userId and role = :role", nativeQuery = true)
    int stampRoleGrantTime(@Param("userId") UUID userId, @Param("role") String role,
                           @Param("grantedAt") Instant grantedAt);

    @Query(value = "select count(*) from authorities "
            + "where role = :role and user_id <> '" + SystemUser.ID_TEXT + "'", nativeQuery = true)
    long countUsersWithRole(@Param("role") String role);

    @EntityGraph(attributePaths = {"profilePicture"})
    List<User> findAllByIdIn(Collection<UUID> ids);
}
