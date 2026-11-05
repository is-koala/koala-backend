package com.exskylab.koala.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.Statistics;
import org.hibernate.stat.internal.StatisticsImpl;
import org.hibernate.stat.spi.StatisticsFactory;
import org.hibernate.stat.spi.StatisticsImplementor;

public final class QueryCounter implements AutoCloseable {

    private final ThreadScopedStatistics statistics;
    private final boolean wasEnabled;
    private final Counts counts = new Counts();

    private QueryCounter(ThreadScopedStatistics statistics) {
        this.statistics = statistics;
        this.wasEnabled = statistics.isStatisticsEnabled();
        statistics.begin(counts);
        statistics.setStatisticsEnabled(true);
    }

    public static QueryCounter start(EntityManagerFactory entityManagerFactory) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        if (!(statistics instanceof ThreadScopedStatistics threadScoped)) {
            throw new IllegalStateException("Hibernate istatistiği " + ThreadScopedStatistics.class.getName()
                    + " değil (" + statistics.getClass().getName() + "); test application.properties içindeki "
                    + "spring.jpa.properties.hibernate.stats.factory kaldırılmış olabilir. Global sayaç arka plan "
                    + "işlerinin sorgularını da sayar (#356).");
        }
        return new QueryCounter(threadScoped);
    }

    public void clear() {
        counts.statements = 0;
        counts.entityFetches = 0;
    }

    public long getPrepareStatementCount() {
        return counts.statements;
    }

    public long getEntityFetchCount() {
        return counts.entityFetches;
    }

    @Override
    public void close() {
        statistics.setStatisticsEnabled(wasEnabled);
        statistics.end();
    }

    static final class Counts {
        long statements;
        long entityFetches;
    }

    public static final class ThreadScopedStatistics extends StatisticsImpl {

        private final ThreadLocal<Counts> measuring = new ThreadLocal<>();

        public ThreadScopedStatistics(SessionFactoryImplementor sessionFactory) {
            super(sessionFactory);
        }

        void begin(Counts counts) {
            if (measuring.get() != null) {
                throw new IllegalStateException("Bu iş parçacığında zaten açık bir QueryCounter var.");
            }
            measuring.set(counts);
        }

        void end() {
            measuring.remove();
        }

        @Override
        public void prepareStatement() {
            super.prepareStatement();
            Counts counts = measuring.get();
            if (counts != null) {
                counts.statements++;
            }
        }

        @Override
        public void fetchEntity(String entityName) {
            super.fetchEntity(entityName);
            Counts counts = measuring.get();
            if (counts != null) {
                counts.entityFetches++;
            }
        }
    }

    public static final class Factory implements StatisticsFactory {

        @Override
        public StatisticsImplementor buildStatistics(SessionFactoryImplementor sessionFactory) {
            return new ThreadScopedStatistics(sessionFactory);
        }
    }
}
