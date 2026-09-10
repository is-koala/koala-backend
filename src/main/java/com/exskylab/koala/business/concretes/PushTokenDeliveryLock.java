package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.privacy.PushTokenMutationLock;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class PushTokenDeliveryLock implements PushTokenMutationLock {

    private static final String MUTATION_LOCK_KEY = "push-token:mutations";
    private static final String LOCK_SQL = "select pg_advisory_lock(hashtextextended(cast(? as text), 0))";
    private static final String TRY_LOCK_SQL = "select pg_try_advisory_lock(hashtextextended(cast(? as text), 0))";
    private static final String UNLOCK_SQL = "select pg_advisory_unlock(hashtextextended(cast(? as text), 0))";
    private static final String TRANSACTION_LOCK_SQL =
            "select pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))";
    private static final String CURRENT_OWNER_SQL = """
            select exists(
                select 1 from devices
                 where id = ? and user_id = ? and push_token = ? and is_deleted = false
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public PushTokenDeliveryLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lockMutations() {
        lockTransactionKey(MUTATION_LOCK_KEY);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lockUser(UUID userId) {
        lockTransactionKey("push-token:user:" + userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lockUsers(Collection<UUID> userIds) {
        userIds.stream()
                .filter(Objects::nonNull)
                .map(userId -> "push-token:user:" + userId)
                .distinct()
                .sorted()
                .forEach(this::lockTransactionKey);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lockTokens(Collection<String> tokens) {
        tokens.stream()
                .filter(Objects::nonNull)
                .filter(token -> !token.isBlank())
                .map(PushTokenDeliveryLock::tokenKey)
                .distinct()
                .sorted()
                .forEach(this::lockTransactionKey);
    }

    public <T> PushTokenDeliveryResult<T> executeIfCurrent(
            UUID recipientId, UUID deviceId, String token, Supplier<T> operation) {
        return jdbcTemplate.execute((ConnectionCallback<PushTokenDeliveryResult<T>>) connection -> {
            String key = tokenKey(token);
            acquire(connection, key);
            return executeWhileLocked(connection, recipientId, deviceId, token, operation, key);
        });
    }

    public <T> PushTokenDeliveryResult<T> tryExecuteIfCurrent(
            UUID recipientId, UUID deviceId, String token, Supplier<T> operation) {
        return jdbcTemplate.execute((ConnectionCallback<PushTokenDeliveryResult<T>>) connection -> {
            String key = tokenKey(token);
            if (!tryAcquire(connection, key)) {
                return PushTokenDeliveryResult.contended();
            }
            return executeWhileLocked(connection, recipientId, deviceId, token, operation, key);
        });
    }

    private <T> PushTokenDeliveryResult<T> executeWhileLocked(
            Connection connection, UUID recipientId, UUID deviceId, String token,
            Supplier<T> operation, String key) throws SQLException {
        Throwable operationFailure = null;
        try {
            if (!isCurrentOwner(connection, recipientId, deviceId, token)) {
                return PushTokenDeliveryResult.notCurrent();
            }
            return PushTokenDeliveryResult.current(operation.get());
        } catch (RuntimeException | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            try {
                release(connection, key);
            } catch (SQLException releaseFailure) {
                abort(connection, releaseFailure);
                if (operationFailure == null) {
                    throw releaseFailure;
                }
                operationFailure.addSuppressed(releaseFailure);
            }
        }
    }

    private boolean isCurrentOwner(Connection connection, UUID recipientId,
                                   UUID deviceId, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CURRENT_OWNER_SQL)) {
            statement.setObject(1, deviceId);
            statement.setObject(2, recipientId);
            statement.setString(3, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void acquire(Connection connection, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
            }
        }
    }

    private boolean tryAcquire(Connection connection, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK_SQL)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void release(Connection connection, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UNLOCK_SQL)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    throw new SQLException("Push token delivery lock was not held");
                }
            }
        }
    }

    private void abort(Connection connection, SQLException releaseFailure) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException abortFailure) {
            releaseFailure.addSuppressed(abortFailure);
        }
    }

    private void lockTransactionKey(String key) {
        jdbcTemplate.queryForObject(TRANSACTION_LOCK_SQL, Object.class, key);
    }

    static String tokenKey(String token) {
        return "push-token:value:" + token;
    }
}
