package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.AdminBootstrapProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    private UserService userService;
    private AdminBootstrapProperties properties;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        properties = new AdminBootstrapProperties();
    }

    private AdminBootstrapRunner runner() {
        return new AdminBootstrapRunner(properties, userService);
    }

    @Test
    void withoutTheVariableTheRunnerTouchesNothing() throws Exception {
        runner().run(null);

        verifyNoInteractions(userService);
    }

    @Test
    void aBlankVariableIsTreatedAsUnset() throws Exception {
        properties.setEmail("   ");

        runner().run(null);

        verifyNoInteractions(userService);
    }

    @Test
    void theConfiguredAccountIsGrantedTheAdminRole() throws Exception {
        properties.setEmail("kurucu@koala.test");
        when(userService.bootstrapAdminRole("kurucu@koala.test")).thenReturn(true);

        runner().run(null);

        verify(userService).bootstrapAdminRole("kurucu@koala.test");
    }

    @Test
    void theEmailIsTrimmedBeforeItIsLookedUp() throws Exception {
        properties.setEmail("  kurucu@koala.test  ");
        when(userService.bootstrapAdminRole(anyString())).thenReturn(true);

        runner().run(null);

        verify(userService).bootstrapAdminRole("kurucu@koala.test");
    }

    @Test
    void asecondStartupWritesNothingMore() throws Exception {
        properties.setEmail("kurucu@koala.test");
        when(userService.bootstrapAdminRole("kurucu@koala.test")).thenReturn(true, false);

        AdminBootstrapRunner runner = runner();
        runner.run(null);
        runner.run(null);

        verify(userService, times(2)).bootstrapAdminRole("kurucu@koala.test");
    }

    @Test
    void aMissingAccountIsWarnedAboutInsteadOfBringingTheApplicationDown() {
        properties.setEmail("yok@koala.test");
        when(userService.bootstrapAdminRole(any()))
                .thenThrow(new ResourceNotFoundException("{user.not.found.by.email}"));

        assertDoesNotThrow(() -> runner().run(null));
    }

    @Test
    void anUnexpectedFailureDoesNotBringTheApplicationDownEither() {
        properties.setEmail("kurucu@koala.test");
        when(userService.bootstrapAdminRole(any())).thenThrow(new IllegalStateException("veritabani yok"));

        assertDoesNotThrow(() -> runner().run(null));
        verify(userService, never()).save(any());
    }
}
