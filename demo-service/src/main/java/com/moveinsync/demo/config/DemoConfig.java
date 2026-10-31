package com.moveinsync.demo.config;

import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.sdk.TokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DemoConfig {

    @Value("${config.client.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${config.client.service-id:demo-service-1}")
    private String serviceId;

    @Value("${config.client.tenants:acme,globex}")
    private String tenantsStr;

    @Value("${config.client.poll-interval-seconds:2}")
    private int pollIntervalSeconds;

    @Value("${config.client.snapshot-dir:./snapshots}")
    private String snapshotDir;

    @Value("${config.client.token:}")
    private String token;

    @Bean(destroyMethod = "close")
    public ConfigClient configClient() {
        List<String> tenants = Arrays.stream(tenantsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        var builder = ConfigClient.builder()
                .serverUrl(serverUrl)
                .serviceId(serviceId)
                .tenants(tenants)
                .pollInterval(Duration.ofSeconds(pollIntervalSeconds))
                .cacheDirectory(Path.of(snapshotDir))
                .failFast(false)
                .startEmpty(true);

        if (token != null && !token.isBlank()) {
            builder.tokenProvider(TokenProvider.staticToken(token));
        }

        return builder.build();
    }
}
