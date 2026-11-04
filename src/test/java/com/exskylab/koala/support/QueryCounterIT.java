package com.exskylab.koala.support;

import com.exskylab.koala.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCounterIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theTestContextBuildsTheThreadScopedStatistics() {
        assertInstanceOf(QueryCounter.ThreadScopedStatistics.class, globalStatistics(),
                "test application.properties hibernate.stats.factory ile QueryCounter.Factory kurmalı");
    }

    @Test
    void aStatementPreparedOnTheMeasuringThreadIsCounted() {
        try (QueryCounter queries = QueryCounter.start(entityManagerFactory)) {
            queries.clear();
            selectOne();
            assertEquals(1, queries.getPrepareStatementCount());

            queries.clear();
            assertEquals(0, queries.getPrepareStatementCount());
        }
    }

    @Test
    void aStatementPreparedOnAnotherThreadIsNotCountedAlthoughTheGlobalCounterSeesIt() throws Exception {
        try (QueryCounter queries = QueryCounter.start(entityManagerFactory)) {
            queries.clear();
            long globalBefore = globalStatistics().getPrepareStatementCount();

            runOnAnotherThread(() -> {
                selectOne();
                selectOne();
                selectOne();
            });

            assertTrue(globalStatistics().getPrepareStatementCount() - globalBefore >= 3,
                    "arka plan iş parçacığının sorguları global Hibernate sayacına düşmeli; düşmüyorsa test "
                            + "kirlenmeyi hiç üretmiyor demektir");
            assertEquals(0, queries.getPrepareStatementCount(),
                    "başka iş parçacığının sorgusu ölçüm penceresine sızdı (#356)");

            selectOne();
            assertEquals(1, queries.getPrepareStatementCount());
        }
    }

    @Test
    void anEntityFetchIsCountedOnlyOnTheMeasuringThread() throws Exception {
        QueryCounter.ThreadScopedStatistics statistics = (QueryCounter.ThreadScopedStatistics) globalStatistics();
        try (QueryCounter queries = QueryCounter.start(entityManagerFactory)) {
            queries.clear();
            runOnAnotherThread(() -> statistics.fetchEntity(User.class.getName()));
            assertEquals(0, queries.getEntityFetchCount(),
                    "başka iş parçacığının lazy yüklemesi ölçüm penceresine sızdı (#356)");

            statistics.fetchEntity(User.class.getName());
            assertEquals(1, queries.getEntityFetchCount());
        }
    }

    @Test
    void closingStopsCountingAndRestoresTheGlobalSwitch() {
        Statistics statistics = globalStatistics();
        boolean before = statistics.isStatisticsEnabled();
        QueryCounter queries = QueryCounter.start(entityManagerFactory);
        assertTrue(statistics.isStatisticsEnabled());
        queries.clear();
        queries.close();

        selectOne();
        assertEquals(0, queries.getPrepareStatementCount());
        assertEquals(before, statistics.isStatisticsEnabled());
    }

    @Test
    void aSecondCounterOnTheSameThreadIsRefused() {
        try (QueryCounter ignored = QueryCounter.start(entityManagerFactory)) {
            assertThrows(IllegalStateException.class, () -> QueryCounter.start(entityManagerFactory));
        }
    }

    @Test
    void startRefusesAFactoryWhoseStatisticsAreNotThreadScoped() {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        SessionFactory sessionFactory = mock(SessionFactory.class);
        when(factory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        when(sessionFactory.getStatistics()).thenReturn(mock(Statistics.class));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> QueryCounter.start(factory));
        assertTrue(refused.getMessage().contains("hibernate.stats.factory"));
    }

    private Statistics globalStatistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void selectOne() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("select 1").getSingleResult());
    }

    private static void runOnAnotherThread(Runnable work) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "query-counter-background");
        thread.start();
        thread.join();
        assertNull(failure.get(), () -> "arka plan işi düştü: " + failure.get());
    }
}
