package com.moveinsync.config.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import com.moveinsync.config.server.repository.DefinitionRepository;
import com.moveinsync.config.server.repository.OverrideRepository;
import com.moveinsync.config.server.state.ServerStateCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

@Component
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DefinitionRepository definitionRepository;
    private final OverrideRepository overrideRepository;
    private final ConfigHeadRepository headRepository;
    private final ServerStateCache stateCache;

    public DemoDataInitializer(
            DefinitionRepository definitionRepository,
            OverrideRepository overrideRepository,
            ConfigHeadRepository headRepository,
            ServerStateCache stateCache
    ) {
        this.definitionRepository = definitionRepository;
        this.overrideRepository = overrideRepository;
        this.headRepository = headRepository;
        this.stateCache = stateCache;
    }

    @Override
    public void run(String... args) {
        if (!definitionRepository.findAll().isEmpty()) {
            log.info("Database already initialized, skipping seed.");
            return;
        }

        log.info("Seeding initial setting definitions and demo overrides...");

        // 1. booking.cutoff (NUMBER 0..1440, default 60)
        SettingDefinition cutoff = new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                NumberValue.of(60),
                ValidationRules.builder().min(0).max(1440).build(),
                "Booking cut-off lead time in minutes"
        );
        definitionRepository.upsert(cutoff, 0L);

        // 2. night.safety.window (TIME_WINDOW, default 22:00-06:00, allowOvernight=true)
        SettingDefinition nightWindow = new SettingDefinition(
                "night.safety.window",
                ValueType.TIME_WINDOW,
                new TimeWindowValue(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                ValidationRules.builder().allowOvernight(true).build(),
                "Night safety operating window"
        );
        definitionRepository.upsert(nightWindow, 0L);

        // 3. night.window.start_offset (DURATION, default PT20H)
        SettingDefinition startOffset = new SettingDefinition(
                "night.window.start_offset",
                ValueType.DURATION,
                new DurationValue(Duration.ofHours(20)),
                ValidationRules.empty(),
                "Night safety window start offset"
        );
        definitionRepository.upsert(startOffset, 0L);

        // 4. night.window.end_offset (DURATION, default PT22H)
        SettingDefinition endOffset = new SettingDefinition(
                "night.window.end_offset",
                ValueType.DURATION,
                new DurationValue(Duration.ofHours(22)),
                ValidationRules.empty(),
                "Night safety window end offset"
        );
        definitionRepository.upsert(endOffset, 0L);

        // 5. max.ride.time (DURATION, default PT120M)
        SettingDefinition maxRideTime = new SettingDefinition(
                "max.ride.time",
                ValueType.DURATION,
                new DurationValue(Duration.ofMinutes(120)),
                ValidationRules.empty(),
                "Maximum ride duration"
        );
        definitionRepository.upsert(maxRideTime, 0L);

        // 6. escort.required (ENUM, default NONE, allowed: MANDATORY, OPTIONAL, NONE)
        SettingDefinition escort = new SettingDefinition(
                "escort.required",
                ValueType.ENUM,
                new EnumValue("NONE"),
                ValidationRules.builder().allowedValues("MANDATORY", "OPTIONAL", "NONE").build(),
                "Security escort requirement"
        );
        definitionRepository.upsert(escort, 0L);

        // 7. cab.types.allowed (LIST, default ["SEDAN", "SUV", "HATCHBACK"])
        SettingDefinition cabTypes = new SettingDefinition(
                "cab.types.allowed",
                ValueType.LIST,
                new ListValue(List.of(new EnumValue("SEDAN"), new EnumValue("SUV"), new EnumValue("HATCHBACK"))),
                ValidationRules.builder().elementType(ValueType.ENUM).maxItems(10).build(),
                "Allowed vehicle types"
        );
        definitionRepository.upsert(cabTypes, 0L);

        // Seed demo offices initial overrides if needed
        overrideRepository.upsert("booking.cutoff", ScopeRef.office("acme/blr-1"), NumberValue.of(60), 0L);
        overrideRepository.upsert("booking.cutoff", ScopeRef.office("acme/blr-2"), NumberValue.of(60), 0L);
        overrideRepository.upsert("booking.cutoff", ScopeRef.office("globex/del-1"), NumberValue.of(60), 0L);

        stateCache.reloadFromDb();
        log.info("Demo data seed completed successfully.");
    }
}

