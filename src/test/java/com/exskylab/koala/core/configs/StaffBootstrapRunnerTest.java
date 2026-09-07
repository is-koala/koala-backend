package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.properties.StaffBootstrapProperties;
import com.exskylab.koala.core.security.StaffBootstrapResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffBootstrapRunnerTest {

    private StaffAccountService staffAccountService;
    private StaffBootstrapProperties properties;
    private StaffBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        staffAccountService = mock(StaffAccountService.class);
        properties = new StaffBootstrapProperties();
        runner = new StaffBootstrapRunner(properties, staffAccountService);
    }

    @Test
    void withoutBothVariablesTheBootstrapNeverRuns() {
        runner.run(null);
        verifyNoInteractions(staffAccountService);
    }

    @Test
    void anEmailWithoutAPasswordIsNotEnough() {
        properties.setOwnerEmail("sahip@iskoala.com");

        runner.run(null);

        verifyNoInteractions(staffAccountService);
    }

    @Test
    void aPasswordWithoutAnEmailIsNotEnoughEither() {
        properties.setOwnerPassword("cok-gizli-bir-parola");

        runner.run(null);

        verifyNoInteractions(staffAccountService);
    }

    @Test
    void blankValuesCountAsMissing() {
        properties.setOwnerEmail("   ");
        properties.setOwnerPassword("   ");

        runner.run(null);

        verifyNoInteractions(staffAccountService);
    }

    @Test
    void bothVariablesTriggerExactlyOneBootstrapAttempt() {
        properties.setOwnerEmail("  sahip@iskoala.com  ");
        properties.setOwnerPassword("cok-gizli-bir-parola");
        when(staffAccountService.bootstrapOwner(any(), any())).thenReturn(new StaffBootstrapResult(true, 0));

        runner.run(null);

        verify(staffAccountService).bootstrapOwner(eq("sahip@iskoala.com"), eq("cok-gizli-bir-parola"));
    }

    @Test
    void aFailingBootstrapNeverStopsTheApplicationFromStarting() {
        properties.setOwnerEmail("sahip@iskoala.com");
        properties.setOwnerPassword("cok-gizli-bir-parola");
        when(staffAccountService.bootstrapOwner(any(), any()))
                .thenThrow(new IllegalStateException("veritabanı yok"));

        assertDoesNotThrow(() -> runner.run(null));
    }
}
