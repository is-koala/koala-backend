package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.EmailVerificationService;
import com.exskylab.koala.business.abstracts.IdentityChallengeService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.PasswordResetTokenService;
import com.exskylab.koala.business.abstracts.PhoneVerificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.mappers.AddressMapper;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import com.exskylab.koala.core.utilities.identity.CscaTrustStore;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleGrantRulesTest {

    private UserDao userDao;
    private SecurityService securityService;
    private UserManager userManager;

    private final UUID actingAdminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        securityService = mock(SecurityService.class);

        userManager = new UserManager(userDao, mock(EmailVerificationService.class), mock(UserMapper.class),
                mock(PasswordEncoder.class), mock(SessionService.class), mock(ImageService.class),
                securityService, mock(AddressService.class),
                mock(AddressMapper.class), mock(UserProducerService.class), mock(BankAccountService.class),
                new IdentityVerificationProperties(),
                new CscaTrustStore(new IdentityVerificationProperties()),
                mock(IdentityChallengeService.class),
                mock(PhoneVerificationService.class), mock(BadgeService.class), mock(BadgeMapper.class),
                new PageableSanitizer(), mock(ActivityNotificationService.class),
                mock(PasswordResetTokenService.class));

        when(userDao.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getAuthenticatedUserId()).thenReturn(actingAdminId);
        when(userDao.findRoleGrants(any())).thenReturn(List.of());
        when(userDao.countUsersWithRole(any())).thenReturn(5L);
    }

    private User user(UUID id, Role... roles) {
        User user = new User();
        user.setId(id);
        user.setAuthorities(new HashSet<>(Set.of(roles)));
        when(userDao.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void anUnknownUserIdIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(userDao.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userManager.getRoleGrants(missing));
        assertThrows(ResourceNotFoundException.class, () -> userManager.grantRole(null, missing, Role.ROLE_ADMIN));
        assertThrows(ResourceNotFoundException.class, () -> userManager.revokeRole(null, missing, Role.ROLE_ADMIN));
    }

    @Test
    void grantingARoleStampsWhoGrantedItAndWhen() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER);
        Set<Role> before = target.getAuthorities();

        userManager.grantRole(null, target.getId(), Role.ROLE_ADMIN);

        assertSame(before, target.getAuthorities(),
                "Koleksiyon ornegi degistirilirse Hibernate rol verme kaydini siler.");
        assertTrue(target.getAuthorities().contains(Role.ROLE_ADMIN));
        verify(userDao).stampRoleGrant(eq(target.getId()), eq(Role.ROLE_ADMIN.name()), any(), eq(actingAdminId));
    }

    @Test
    void grantingARoleTheUserAlreadyHasWritesNothing() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);

        userManager.grantRole(null, target.getId(), Role.ROLE_ADMIN);

        assertEquals(2, target.getAuthorities().size());
        verify(userDao, never()).stampRoleGrant(any(), any(), any(), any());
        verify(userDao, never()).saveAndFlush(any());
    }

    @Test
    void anAdminCannotTakeAwayTheirOwnAdminRole() {
        user(actingAdminId, Role.ROLE_USER, Role.ROLE_ADMIN);

        assertThrows(ConflictException.class, () -> userManager.revokeRole(null, actingAdminId, Role.ROLE_ADMIN));
    }

    @Test
    void theLastAdminRoleInTheSystemCannotBeTakenAway() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);
        when(userDao.countUsersWithRole(Role.ROLE_ADMIN.name())).thenReturn(1L);

        assertThrows(ConflictException.class, () -> userManager.revokeRole(null, target.getId(), Role.ROLE_ADMIN));
        assertTrue(target.getAuthorities().contains(Role.ROLE_ADMIN));
    }

    @Test
    void theSecondToLastAdminRoleIsStillRevocable() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);
        when(userDao.countUsersWithRole(Role.ROLE_ADMIN.name())).thenReturn(2L);

        userManager.revokeRole(null, target.getId(), Role.ROLE_ADMIN);

        assertFalse(target.getAuthorities().contains(Role.ROLE_ADMIN));
    }

    @Test
    void theMarketplaceMembershipRoleIsNotRevocableHere() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);

        assertThrows(ConflictException.class, () -> userManager.revokeRole(null, target.getId(), Role.ROLE_USER));
        assertTrue(target.getAuthorities().contains(Role.ROLE_USER));
    }

    @Test
    void theSystemUserKeepsItsRolesWhateverIsAsked() {
        user(SystemUser.ID, Role.ROLE_USER, Role.ROLE_ADMIN);

        assertThrows(ConflictException.class, () -> userManager.grantRole(null, SystemUser.ID, Role.ROLE_ADMIN));
        assertThrows(ConflictException.class, () -> userManager.revokeRole(null, SystemUser.ID, Role.ROLE_ADMIN));
    }

    @Test
    void revokingARoleTheUserNeverHadChangesNothing() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER);

        userManager.revokeRole(null, target.getId(), Role.ROLE_ADMIN);

        assertEquals(Set.of(Role.ROLE_USER), target.getAuthorities());
        verify(userDao, never()).saveAndFlush(any());
    }

    @Test
    void theBootstrapGrantLeavesNoGranterAndIsIdempotent() {
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("kurucu@koala.test");
        target.setAuthorities(new HashSet<>(Set.of(Role.ROLE_USER)));
        when(userDao.findByEmail("kurucu@koala.test")).thenReturn(Optional.of(target));

        assertTrue(userManager.bootstrapAdminRole("kurucu@koala.test"));
        assertFalse(userManager.bootstrapAdminRole("kurucu@koala.test"));

        verify(userDao, times(1)).stampRoleGrantTime(eq(target.getId()), eq(Role.ROLE_ADMIN.name()), any());
        verify(userDao, never()).stampRoleGrant(any(), any(), any(), any());
    }

    @Test
    void theBootstrapGrantSaysSoWhenThereIsNoSuchUser() {
        when(userDao.findByEmail("yok@koala.test")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userManager.bootstrapAdminRole("yok@koala.test"));
    }

    private static StaffPrincipal staff() {
        return StaffPrincipal.unregistered("kc-subject-1", Set.of("OWNER"));
    }

    @Test
    void grantRoleWritesNoGrantedByForAStaffActorAndNeverTouchesTheMarketplacePrincipal() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER);

        userManager.grantRole(staff(), target.getId(), Role.ROLE_ADMIN);

        verify(userDao).stampRoleGrantTime(eq(target.getId()), eq(Role.ROLE_ADMIN.name()), any());
        verify(userDao, never()).stampRoleGrant(any(), any(), any(), any());
        verify(securityService, never()).getAuthenticatedUserId();
        verify(securityService, never()).getAuthenticatedUser();
    }

    @Test
    void revokeRoleForAStaffActorSkipsTheSelfRevokeCheckAndNeverTouchesTheMarketplacePrincipal() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);
        when(userDao.countUsersWithRole(Role.ROLE_ADMIN.name())).thenReturn(2L);

        userManager.revokeRole(staff(), target.getId(), Role.ROLE_ADMIN);

        assertFalse(target.getAuthorities().contains(Role.ROLE_ADMIN));
        verify(securityService, never()).getAuthenticatedUserId();
        verify(securityService, never()).getAuthenticatedUser();
    }

    @Test
    void revokeRoleStillProtectsTheLastAdminForAStaffActor() {
        User target = user(UUID.randomUUID(), Role.ROLE_USER, Role.ROLE_ADMIN);
        when(userDao.countUsersWithRole(Role.ROLE_ADMIN.name())).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> userManager.revokeRole(staff(), target.getId(), Role.ROLE_ADMIN));
        assertTrue(target.getAuthorities().contains(Role.ROLE_ADMIN));
    }
}
