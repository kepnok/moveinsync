package com.moveinsync.demo.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.sdk.ConfigView;
import com.moveinsync.config.sdk.stats.ClientStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfigClient configClient;

    @Test
    void getCutoffReturnsResolvedValue() throws Exception {
        UUID epoch = UUID.randomUUID();
        SettingDefinition def = new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                new NumberValue(new BigDecimal("60")),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Cutoff"
        );
        ConfigSnapshot snapshot = ConfigSnapshot.empty(epoch)
                .withChangeSet(new ChangeSet(1L, UUID.randomUUID(), ChangeKind.DEFINITION, "sys", "init", Instant.now(),
                        List.of(new DefinitionChange("booking.cutoff", null, def)), null, null))
                .withChangeSet(new ChangeSet(2L, UUID.randomUUID(), ChangeKind.NORMAL, "dev", "override", Instant.now(),
                        List.of(new OverrideChange("booking.cutoff", ScopeRef.parse("OFFICE:acme/blr-1"), null, new NumberValue(new BigDecimal("30")))),
                        null, null));

        ConfigView view = new ConfigView(snapshot, new ConfigResolver());
        when(configClient.view("acme")).thenReturn(view);

        mockMvc.perform(get("/demo/cutoff")
                        .param("client", "acme")
                        .param("office", "acme/blr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("booking.cutoff"))
                .andExpect(jsonPath("$.value").value(30))
                .andExpect(jsonPath("$.source").value("OFFICE:acme/blr-1"))
                .andExpect(jsonPath("$.snapshotVersion").value(2));
    }

    @Test
    void getStatsReturnsClientStats() throws Exception {
        ClientStats stats = new ClientStats(
                5L, 1000L, 50L, 3L, 0L, 0L, 0L, 1L, 2L, 0L, true
        );
        when(configClient.stats()).thenReturn(stats);

        mockMvc.perform(get("/demo/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.healthy").value(true));
    }
}
