package com.exskylab.koala.dataAccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SitemapManifestDaoTest {

    @Test
    void shardReadsExposeAnInternalPersistenceProjection() throws Exception {
        var method = SitemapManifestDao.class.getMethod("readShard", UUID.class, long.class, long.class);
        var returnType = (ParameterizedType) method.getGenericReturnType();

        assertEquals("com.exskylab.koala.dataAccess.SitemapManifestDao$SitemapEntryRecord",
                returnType.getActualTypeArguments()[0].getTypeName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void materializationUsesTheLargeJdbcUpdateCount() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeLargeUpdate()).thenReturn(2_500_000_000L);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<Long> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        SitemapManifestDao dao = new SitemapManifestDao(jdbc, mock(DataSourceProperties.class));
        UUID version = UUID.randomUUID();
        LocalDateTime availableAfter = LocalDateTime.parse("2026-09-09T12:00:00");

        long materialized = dao.materialize(version, availableAfter);

        assertEquals(2_500_000_000L, materialized);
        verify(statement).setObject(1, version);
        verify(statement).setObject(2, availableAfter);
    }
}
