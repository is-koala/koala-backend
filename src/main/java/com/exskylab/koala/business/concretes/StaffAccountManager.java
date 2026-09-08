package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.response.StaffTokenDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.StaffBranchReach;
import com.exskylab.koala.core.helpers.StaffSeparationOfDuties;
import com.exskylab.koala.core.properties.StaffAuthProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffBootstrapResult;
import com.exskylab.koala.core.security.StaffJwtIdentity;
import com.exskylab.koala.core.security.StaffMemberView;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.StaffMemberDao;
import com.exskylab.koala.dataAccess.StaffMemberRoleDao;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffMember;
import com.exskylab.koala.entities.StaffMemberRole;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffRole;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StaffAccountManager implements StaffAccountService {

    private static final String ABSENT_ACCOUNT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa9WVJ4RJ2qeYJEHDzWJHpj0pZGZ.iOe";

    private static final Set<StaffPermission> ADMINISTRATION_PERMISSIONS =
            Set.of(StaffPermission.STAFF_MANAGE, StaffPermission.ROLE_MANAGE);

    private static final Set<String> SORTABLE = Set.of("createdAt", "email", "displayName");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final StaffMemberDao staffMemberDao;
    private final StaffMemberRoleDao staffMemberRoleDao;
    private final StaffAuthorizationService staffAuthorizationService;
    private final StaffAuditService staffAuditService;
    private final PasswordEncoder passwordEncoder;
    private final StaffAuthProperties staffAuthProperties;
    private final JwtService jwtService;
    private final PageableSanitizer pageableSanitizer;

    public StaffAccountManager(StaffMemberDao staffMemberDao,
                               StaffMemberRoleDao staffMemberRoleDao,
                               StaffAuthorizationService staffAuthorizationService,
                               StaffAuditService staffAuditService,
                               PasswordEncoder passwordEncoder,
                               StaffAuthProperties staffAuthProperties,
                               JwtService jwtService,
                               PageableSanitizer pageableSanitizer) {
        this.staffMemberDao = staffMemberDao;
        this.staffMemberRoleDao = staffMemberRoleDao;
        this.staffAuthorizationService = staffAuthorizationService;
        this.staffAuditService = staffAuditService;
        this.passwordEncoder = passwordEncoder;
        this.staffAuthProperties = staffAuthProperties;
        this.jwtService = jwtService;
        this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public StaffTokenDto login(String email, String rawPassword) {
        StaffMember member = staffMemberDao.findByEmailIgnoreCase(email.trim()).orElse(null);

        if (member == null) {
            passwordEncoder.matches(rawPassword, ABSENT_ACCOUNT_HASH);
            log.warn("Personel girişi reddedildi, kayıt yok: {}", LogMasks.email(email));
            throw new UnauthorizedException(StaffMessages.LOGIN_FAILED);
        }

        Instant now = Instant.now();

        if (isLocked(member, now)) {
            passwordEncoder.matches(rawPassword, ABSENT_ACCOUNT_HASH);
            log.warn("Personel girişi reddedildi, hesap kilitli: {} kilit={}",
                    LogMasks.email(member.getEmail()), member.getLockedUntil());
            throw new UnauthorizedException(StaffMessages.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            registerFailure(member, now);
            log.warn("Personel girişi reddedildi, parola hatalı: {}", LogMasks.email(member.getEmail()));
            throw new UnauthorizedException(StaffMessages.LOGIN_FAILED);
        }

        if (!member.isActive()) {
            log.warn("Personel girişi reddedildi, hesap devre dışı: {}", LogMasks.email(member.getEmail()));
            throw new UnauthorizedException(StaffMessages.LOGIN_FAILED);
        }

        member.setFailedLoginAttempts(0);
        member.setLockedUntil(null);
        staffMemberDao.save(member);

        return tokensFor(member);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffTokenDto refresh(String refreshToken) {
        StaffJwtIdentity identity;
        try {
            identity = jwtService.extractStaffIdentity(refreshToken, JwtService.STAFF_REFRESH_AUDIENCE);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Personel yenileme token'ı doğrulanamadı: {}", exception.getMessage());
            throw new UnauthorizedException(StaffMessages.TOKEN_INVALID);
        }

        StaffMember member = staffMemberDao.findById(identity.staffId()).orElse(null);

        if (member == null || !member.isActive() || isLocked(member, Instant.now())
                || member.getTokenVersion() != identity.tokenVersion()) {
            throw new UnauthorizedException(StaffMessages.SESSION_ENDED);
        }

        return tokensFor(member);
    }

    @Override
    @Transactional
    public void logout(StaffPrincipal actor) {
        StaffMember member = memberById(actor.staffId());
        member.setTokenVersion(member.getTokenVersion() + 1);
        staffMemberDao.save(member);
    }

    private StaffTokenDto tokensFor(StaffMember member) {
        Instant now = Instant.now();
        Duration accessValidity = staffAuthProperties.getAccessTokenValidity();
        Duration refreshValidity = staffAuthProperties.getRefreshTokenValidity();

        return new StaffTokenDto(
                jwtService.generateStaffToken(member.getId(), member.getTokenVersion(),
                        JwtService.STAFF_ACCESS_AUDIENCE, accessValidity),
                jwtService.generateStaffToken(member.getId(), member.getTokenVersion(),
                        JwtService.STAFF_REFRESH_AUDIENCE, refreshValidity),
                now.plus(accessValidity),
                now.plus(refreshValidity),
                member.isMustChangePassword());
    }

    private void registerFailure(StaffMember member, Instant now) {
        int attempts = member.getFailedLoginAttempts() + 1;

        if (attempts >= staffAuthProperties.getMaxFailedAttempts()) {
            member.setFailedLoginAttempts(0);
            member.setLockedUntil(now.plus(staffAuthProperties.getLockDuration()));
            log.warn("Personel hesabı kilitlendi: {} deneme={} kilit={}",
                    LogMasks.email(member.getEmail()), attempts, member.getLockedUntil());
        } else {
            member.setFailedLoginAttempts(attempts);
        }

        staffMemberDao.save(member);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffPrincipal> principalOf(UUID staffId, int tokenVersion) {
        StaffMember member = staffMemberDao.findById(staffId).orElse(null);

        if (member == null || !member.isActive() || member.getTokenVersion() != tokenVersion) {
            return Optional.empty();
        }

        Set<String> memberships = roleCodesOf(staffId);
        StaffAuthorization authorization = staffAuthorizationService.resolve(memberships);

        return Optional.of(new StaffPrincipal(
                member.getId(),
                member.getEmail(),
                displayNameOf(member),
                authorization.roles(),
                authorization.permissions(),
                member.isMustChangePassword()));
    }

    @Override
    @Transactional
    public void changeOwnPassword(StaffPrincipal actor, String currentPassword, String newPassword) {
        StaffMember member = memberById(actor.staffId());

        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            log.warn("Personel parola değişikliği reddedildi, mevcut parola hatalı: {}",
                    LogMasks.email(member.getEmail()));
            throw new UnauthorizedException(StaffMessages.LOGIN_FAILED);
        }

        if (passwordEncoder.matches(newPassword, member.getPasswordHash())) {
            throw new ConflictException(StaffMessages.PASSWORD_UNCHANGED);
        }

        member.setPasswordHash(passwordEncoder.encode(newPassword));
        member.setMustChangePassword(false);
        member.setFailedLoginAttempts(0);
        member.setLockedUntil(null);
        member.setTokenVersion(member.getTokenVersion() + 1);
        staffMemberDao.save(member);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StaffMemberView> search(String query, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        Page<StaffMember> page = staffMemberDao.search(
                query == null || query.isBlank() ? null : query.trim(),
                pageableSanitizer.sanitize(pageable, SORTABLE, NEWEST_FIRST));

        Map<UUID, List<String>> rolesOf = roleCodesOf(page.getContent().stream().map(StaffMember::getId).toList());

        return page.map(member -> new StaffMemberView(member,
                rolesOf.getOrDefault(member.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public StaffMemberView get(UUID staffId) {
        StaffMember member = memberById(staffId);
        return new StaffMemberView(member, List.copyOf(roleCodesOf(staffId)));
    }

    @Override
    @Transactional
    public StaffMemberView create(String email, String displayName, String rawPassword) {
        String normalized = email.trim();

        if (staffMemberDao.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException(StaffMessages.EMAIL_ALREADY_REGISTERED);
        }

        StaffMember member = new StaffMember();
        member.setEmail(normalized);
        member.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName.trim());
        member.setPasswordHash(passwordEncoder.encode(rawPassword));
        member.setMustChangePassword(true);
        member.setActive(true);
        staffMemberDao.save(member);

        StaffAuditContext.capture(null, "email=" + normalized + "; roles=[]");

        return new StaffMemberView(member, List.of());
    }

    @Override
    @Transactional
    public StaffMemberView setActive(StaffPrincipal actor, UUID staffId, boolean active) {
        StaffMember member = memberById(staffId);
        String before = trailOf(member);

        member.setActive(active);
        if (!active) {
            member.setTokenVersion(member.getTokenVersion() + 1);
        }
        staffMemberDao.save(member);
        staffMemberDao.flush();

        requireAnAdministratorSurvives(actor, member);
        StaffAuditContext.capture(before, trailOf(member));

        return new StaffMemberView(member, List.copyOf(roleCodesOf(staffId)));
    }

    @Override
    @Transactional
    public void grantRole(StaffPrincipal actor, UUID staffId, String roleCode) {
        StaffMember member = memberById(staffId);
        StaffRole role = staffAuthorizationService.getRoleByCode(roleCode);

        requireTheActorReachesEveryPermissionOf(actor, role);

        if (staffMemberRoleDao.existsByMemberIdAndRoleId(member.getId(), role.getId())) {
            return;
        }

        requireTheBranchesStaySeparateAfter(member, role);

        String before = trailOf(member);

        StaffMemberRole membership = new StaffMemberRole();
        membership.setMember(member);
        membership.setRole(role);
        staffMemberRoleDao.save(membership);
        staffMemberRoleDao.flush();

        StaffAuditContext.capture(before, trailOf(member));
    }

    @Override
    @Transactional
    public void revokeRole(StaffPrincipal actor, UUID staffId, String roleCode) {
        StaffMember member = memberById(staffId);
        StaffRole role = staffAuthorizationService.getRoleByCode(roleCode);

        requireTheActorReachesEveryPermissionOf(actor, role);

        String before = trailOf(member);

        if (staffMemberRoleDao.deleteMembership(member.getId(), role.getId()) == 0) {
            return;
        }
        staffMemberRoleDao.flush();

        requireAnAdministratorSurvives(actor, member);
        StaffAuditContext.capture(before, trailOf(member));
    }

    @Override
    @Transactional
    public void resetPassword(StaffPrincipal actor, UUID staffId, String rawPassword) {
        StaffMember member = memberById(staffId);

        member.setPasswordHash(passwordEncoder.encode(rawPassword));
        member.setMustChangePassword(true);
        member.setFailedLoginAttempts(0);
        member.setLockedUntil(null);
        member.setTokenVersion(member.getTokenVersion() + 1);
        staffMemberDao.save(member);

        log.info("Personel parolası yönetici tarafından atandı: hedef={} aktör={}",
                LogMasks.email(member.getEmail()), actor.staffId());
    }

    @Override
    @Transactional
    public StaffBootstrapResult bootstrapOwner(String email, String rawPassword) {
        long existing = staffMemberDao.count();

        if (existing > 0) {
            return new StaffBootstrapResult(false, existing);
        }

        StaffRole owner = staffAuthorizationService.getRoleByCode("OWNER");

        StaffMember member = new StaffMember();
        member.setEmail(email.trim());
        member.setDisplayName("Bootstrap OWNER");
        member.setPasswordHash(passwordEncoder.encode(rawPassword));
        member.setMustChangePassword(true);
        member.setActive(true);
        staffMemberDao.save(member);

        StaffMemberRole membership = new StaffMemberRole();
        membership.setMember(member);
        membership.setRole(owner);
        staffMemberRoleDao.save(membership);

        staffAuditService.record(new StaffAuditRecord(
                member.getId(),
                "bootstrap",
                StaffPermission.STAFF_MANAGE.getCode(),
                "staff.bootstrap",
                "staff",
                member.getId().toString(),
                true,
                true,
                PermissionSensitivity.PERSONAL_DATA,
                null,
                null), null, null, "email=" + member.getEmail() + "; roles=[OWNER]");

        return new StaffBootstrapResult(true, 0);
    }

    private void requireTheActorReachesEveryPermissionOf(StaffPrincipal actor, StaffRole role) {
        if (actor == null || !actor.isRegistered()) {
            throw new ForbiddenException(StaffMessages.PERMISSION_NOT_GRANTABLE);
        }

        Set<StaffPermission> held = staffAuthorizationService.resolve(actor.roles()).permissions();
        Set<StaffPermission> demanded = staffAuthorizationService.getRole(role.getId()).effectivePermissions();

        if (!held.containsAll(demanded)) {
            throw new ForbiddenException(StaffMessages.PERMISSION_NOT_GRANTABLE);
        }
    }

    private void requireTheBranchesStaySeparateAfter(StaffMember member, StaffRole granted) {
        Set<String> assignedAfterGrant = new LinkedHashSet<>(roleCodesOf(member.getId()));
        assignedAfterGrant.add(granted.getCode());

        StaffSeparationOfDuties.requireBranchesStaySeparate(
                StaffBranchReach.assignedRoles(assignedAfterGrant)
                        .reaching(staffAuthorizationService.resolve(assignedAfterGrant).roles()));
    }

    private void requireAnAdministratorSurvives(StaffPrincipal actor, StaffMember target) {
        if (actor != null && actor.isRegistered() && actor.staffId().equals(target.getId())) {
            Set<StaffPermission> remaining = target.isActive()
                    ? staffAuthorizationService.resolve(roleCodesOf(target.getId())).permissions()
                    : Set.of();

            if (!remaining.containsAll(ADMINISTRATION_PERMISSIONS)) {
                throw new ConflictException(StaffMessages.MEMBERSHIP_SELF_LOCKOUT);
            }
        }

        for (StaffPermission permission : ADMINISTRATION_PERMISSIONS) {
            if (countActiveHoldersOf(permission) == 0) {
                throw new ConflictException(StaffMessages.LAST_ADMINISTRATOR);
            }
        }
    }

    private long countActiveHoldersOf(StaffPermission permission) {
        Set<UUID> roleIds = new LinkedHashSet<>();

        staffAuthorizationService.getRoles().stream()
                .filter(view -> view.effectivePermissions().contains(permission))
                .forEach(view -> roleIds.add(view.role().getId()));

        return roleIds.isEmpty() ? 0 : staffMemberRoleDao.countActiveMembersInRoles(roleIds);
    }

    private Set<String> roleCodesOf(UUID staffId) {
        Set<String> codes = new LinkedHashSet<>();
        staffMemberRoleDao.findAllByMemberId(staffId)
                .forEach(membership -> codes.add(membership.getRole().getCode()));
        return codes;
    }

    private Map<UUID, List<String>> roleCodesOf(Collection<UUID> staffIds) {
        Map<UUID, List<String>> rolesOf = new LinkedHashMap<>();
        if (staffIds.isEmpty()) {
            return rolesOf;
        }

        for (StaffMemberRole membership : staffMemberRoleDao.findAllByMemberIdIn(staffIds)) {
            rolesOf.computeIfAbsent(membership.getMember().getId(), key -> new ArrayList<>())
                    .add(membership.getRole().getCode());
        }
        rolesOf.values().forEach(java.util.Collections::sort);
        return rolesOf;
    }

    private String trailOf(StaffMember member) {
        List<String> codes = new ArrayList<>(roleCodesOf(member.getId()));
        java.util.Collections.sort(codes);
        return "active=" + member.isActive() + "; roles=[" + String.join(", ", codes) + "]";
    }

    private StaffMember memberById(UUID staffId) {
        return staffMemberDao.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.RECORD_NOT_FOUND));
    }

    private static String displayNameOf(StaffMember member) {
        String name = member.getDisplayName();
        return name == null || name.isBlank() ? member.getEmail() : name;
    }


    private static boolean isLocked(StaffMember member, Instant now) {
        return member.getLockedUntil() != null && member.getLockedUntil().isAfter(now);
    }

}
