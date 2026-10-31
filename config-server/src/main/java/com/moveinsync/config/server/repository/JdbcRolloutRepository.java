package com.moveinsync.config.server.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcRolloutRepository implements RolloutRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRolloutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(RolloutRecord r) {
        jdbcTemplate.update("""
            INSERT INTO rollouts (id, status, tenant_id, stage_version, final_version, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                r.id(),
                r.status(),
                r.tenantId(),
                r.stageVersion(),
                r.finalVersion(),
                r.createdBy(),
                Timestamp.from(r.createdAt())
        );

        for (String office : r.targetOffices()) {
            jdbcTemplate.update("INSERT INTO rollout_targets (rollout_id, office_scope_id) VALUES (?, ?)", r.id(), office);
        }
    }

    @Override
    public Optional<RolloutRecord> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, status, tenant_id, stage_version, final_version, created_by, created_at FROM rollouts WHERE id = ?",
                    (rs, rowNum) -> {
                        String status = rs.getString("status");
                        String tenantId = rs.getString("tenant_id");
                        long stageVersion = rs.getLong("stage_version");
                        Long finalVersion = rs.getObject("final_version") != null ? rs.getLong("final_version") : null;
                        String createdBy = rs.getString("created_by");
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();
                        Set<String> targets = targets(id);
                        return new RolloutRecord(id, status, tenantId, stageVersion, finalVersion, createdBy, createdAt, targets);
                    },
                    id
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateStatus(UUID id, String status, Long finalVersion) {
        jdbcTemplate.update("UPDATE rollouts SET status = ?, final_version = ? WHERE id = ?", status, finalVersion, id);
    }

    @Override
    public Set<String> targets(UUID id) {
        List<String> list = jdbcTemplate.query(
                "SELECT office_scope_id FROM rollout_targets WHERE rollout_id = ?",
                (rs, rowNum) -> rs.getString("office_scope_id"),
                id
        );
        return Collections.unmodifiableSet(new HashSet<>(list));
    }

    @Override
    public List<RolloutRecord> findByTenantAndStatus(String tenantId, String status) {
        return jdbcTemplate.query(
                "SELECT id, status, tenant_id, stage_version, final_version, created_by, created_at FROM rollouts WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    long stageVersion = rs.getLong("stage_version");
                    Long finalVersion = rs.getObject("final_version") != null ? rs.getLong("final_version") : null;
                    String createdBy = rs.getString("created_by");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    Set<String> targets = targets(id);
                    return new RolloutRecord(id, status, tenantId, stageVersion, finalVersion, createdBy, createdAt, targets);
                },
                tenantId,
                status
        );
    }
}

