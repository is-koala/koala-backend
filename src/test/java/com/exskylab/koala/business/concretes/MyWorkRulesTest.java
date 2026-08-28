package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MyWorkRulesTest {

    private SessionDao sessionDao;
    private SecurityService securityService;
    private SessionManager sessionManager;

    private final User me = user();
    private final User someoneElse = user();
    private Session currentSession;

    @BeforeEach
    void setUp() {
        sessionDao = mock(SessionDao.class);
        securityService = mock(SecurityService.class);

        sessionManager = new SessionManager(sessionDao, mock(PasswordEncoder.class),
                mock(JwtService.class), new SessionProperties(), securityService);

        when(sessionDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityService.getAuthenticatedUserId()).thenReturn(me.getId());

        currentSession = session(me);
        when(securityService.getAuthenticatedPrincipal())
                .thenReturn(new UserPrincipal(me.getId(), "me@koala.test", Set.of(Role.ROLE_USER), currentSession.getId()));
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private Session session(User owner) {
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUser(owner);

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setDevice(device);
        session.setActive(true);

        when(sessionDao.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void aSessionBelongingToSomeoneElseCannotBeRevoked() {
        Session theirs = session(someoneElse);

        assertThrows(ResourceNotFoundException.class, () -> sessionManager.revokeSession(theirs.getId()));
        assertTrue(theirs.isActive());
        verify(sessionDao, never()).save(any());
    }

    @Test
    void revokingMyOwnSessionDeactivatesIt() {
        Session mine = session(me);

        sessionManager.revokeSession(mine.getId());

        assertFalse(mine.isActive());
    }

    @Test
    void revokingEverythingElseSparesTheSessionMakingTheRequest() {
        Session other = session(me);
        when(sessionDao.findAllByDevice_User_IdAndIsActive(me.getId(), true))
                .thenReturn(List.of(currentSession, other));

        int revoked = sessionManager.revokeAllOtherSessions();

        assertEquals(1, revoked);
        assertTrue(currentSession.isActive());
        assertFalse(other.isActive());
    }

    @Test
    void aUserWithOnlyOneSessionRevokesNothing() {
        when(sessionDao.findAllByDevice_User_IdAndIsActive(me.getId(), true))
                .thenReturn(List.of(currentSession));

        assertEquals(0, sessionManager.revokeAllOtherSessions());
        assertTrue(currentSession.isActive());
    }

    @Test
    void notificationsAndUnreadCountsAreScopedToTheSignedInUser() {
        NotificationDao notificationDao = mock(NotificationDao.class);
        when(notificationDao.countByRecipientIdAndReadAtIsNull(me.getId())).thenReturn(4L);
        when(notificationDao.countByRecipientIdAndReadAtIsNull(someoneElse.getId())).thenReturn(99L);

        assertEquals(4L, notificationDao.countByRecipientIdAndReadAtIsNull(securityService.getAuthenticatedUserId()));
    }
}
