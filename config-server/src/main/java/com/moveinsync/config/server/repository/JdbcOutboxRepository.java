package com.moveinsync.config.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(long version, Set<String> tenants) {
        String[] arr = tenants != null ? tenants.toArray(new String[0]) : new String[0];
        jdbcTemplate.update((conn) -> {
            var ps = conn.prepareStatement("INSERT INTO outbox (version, tenants) VALUES (?, ?)");
            ps.setLong(1, version);
            Array sqlArr = conn.createArrayOf("text", arr);
            ps.setArray(2, sqlArr);
            return ps;
        });
    }

    @Override
    public List<OutboxRow> lockPending(int batchSize) {
        return jdbcTemplate.query(
                "SELECT id, version, tenants, created_at, published_at FROM outbox WHERE published_at IS NULL ORDER BY version ASC LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    long version = rs.getLong("version");
                    Array sqlArray = rs.getArray("tenants");
                    Set<String> tenants = new HashSet<>();
                    if (sqlArray != null) {
                        String[] arr = (String[]) sqlArray.getArray();
                        if (arr != null) {
                            tenants.addAll(Arrays.asList(arr));
                        }
                    }
                    Instant created = rs.getTimestamp("created_at").toInstant();
                    Timestamp pubTs = rs.getTimestamp("published_at");
                    Instant published = pubTs != null ? pubTs.toInstant() : null;
                    return new OutboxRow(id, version, tenants, created, published);
                },
                batchSize
        );
    }

    @Override
    public void markPublished(long version) {
        jdbcTemplate.update("UPDATE outbox SET published_at = now() WHERE version = ?", version);
    }

    @Override
    public long pendingCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class);
        return count != null ? count : 0L;
    }
}

