package com.moveinsync.config.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.ConfigResolver;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import com.moveinsync.config.core.validation.ChangeSetValidator;
import com.moveinsync.config.core.validation.CrossFieldRule;
import com.moveinsync.config.core.validation.CutoffWithinLeadTimeRule;
import com.moveinsync.config.core.validation.MinLessThanMaxRideTimeRule;
import com.moveinsync.config.core.validation.StartBeforeEndRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public ValueTypeRegistry valueTypeRegistry() {
        return new ValueTypeRegistry();
    }

    @Bean
    public ConfigResolver configResolver() {
        return new ConfigResolver();
    }

    @Bean
    public List<CrossFieldRule> crossFieldRules() {
        return List.of(
                new StartBeforeEndRule(),
                new MinLessThanMaxRideTimeRule(),
                new CutoffWithinLeadTimeRule()
        );
    }

    @Bean
    public ChangeSetValidator changeSetValidator(ValueTypeRegistry registry, List<CrossFieldRule> rules, ConfigResolver resolver) {
        return new ChangeSetValidator(registry, rules, resolver);
    }

    @Bean
    public SnapshotCodec snapshotCodec(ValueTypeRegistry registry) {
        return new SnapshotCodec(registry);
    }
}

