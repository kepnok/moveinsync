package com.moveinsync.config.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcConfigHeadRepository implements ConfigHeadRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcConfigHeadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Head lockForUpdate() {
        return jdbcTemplate.queryForObject(
                "SELECT version, epoch FROM config_head WHERE id = 1 FOR UPDATE",
                (rs, rowNum) -> new Head(rs.getLong("version"), (UUID) rs.getObject("epoch"))
        );
    }

    @Override
    public Head getHead() {
        return jdbcTemplate.queryForObject(
                "SELECT version, epoch FROM config_head WHERE id = 1",
                (rs, rowNum) -> new Head(rs.getLong("version"), (UUID) rs.getObject("epoch"))
        );
    }

    @Override
    public void advance(long newVersion) {
        jdbcTemplate.update("UPDATE config_head SET version = ? WHERE id = 1", newVersion);
    }

    @Override
    public void resetEpoch(UUID newEpoch, long version) {
        jdbcTemplate.update("UPDATE config_head SET epoch = ?, version = ? WHERE id = 1", newEpoch, version);
    }
}

