package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedUserFreshnessTest {

    private UserDao userDao;
    private SecurityManager securityManager;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        securityManager = new SecurityManager(userDao);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(userId, "ada@example.com", Set.of(Role.ROLE_USER), null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User storedUser(String firstName) {
        User user = new User();
        user.setId(userId);
        user.setFirstName(firstName);
        return user;
    }

    @Test
    void theUserIsReadFromTheDatabaseEveryTimeNotFromTheContext() {
        authenticate();
        when(userDao.findById(userId))
                .thenReturn(Optional.of(storedUser("Ada")))
                .thenReturn(Optional.of(storedUser("Bilge")));

        assertEquals("Ada", securityManager.getAuthenticatedUser().getFirstName());
        assertEquals("Bilge", securityManager.getAuthenticatedUser().getFirstName(),
                "Ikinci okuma veritabanindaki guncel hali dondurmeliydi.");

        verify(userDao, times(2)).findById(userId);
    }

    @Test
    void twoReadsReturnDistinctInstancesSoNeitherCanBeStale() {
        authenticate();
        when(userDao.findById(userId)).thenReturn(Optional.of(storedUser("Ada")))
                .thenReturn(Optional.of(storedUser("Ada")));

        assertNotSame(securityManager.getAuthenticatedUser(), securityManager.getAuthenticatedUser());
    }

    @Test
    void readingTheIdDoesNotTouchTheDatabase() {
        authenticate();

        assertEquals(userId, securityManager.getAuthenticatedUserId());

        verify(userDao, times(0)).findById(userId);
    }

    @Test
    void anAnonymousContextIsRejected() {
        assertThrows(ResourceNotFoundException.class, () -> securityManager.getAuthenticatedUserId());
        assertThrows(ResourceNotFoundException.class, () -> securityManager.getAuthenticatedUser());
    }

    @Test
    void aDeletedUserIsReportedInsteadOfReturningAStaleCopy() {
        authenticate();
        when(userDao.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> securityManager.getAuthenticatedUser());
    }
}
