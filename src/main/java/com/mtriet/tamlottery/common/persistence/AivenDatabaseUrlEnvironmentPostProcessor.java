package com.mtriet.tamlottery.common.persistence;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.Map;

public final class AivenDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "tamLotteryAivenDatabase";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("aiven"::equalsIgnoreCase)) {
            return;
        }
        String configuredUrl = environment.getProperty("DB_URL");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return;
        }
        String sanitizedUrl = AivenDatabaseUrl.toJdbcUrl(configuredUrl);
        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(
                        "DB_URL", sanitizedUrl,
                        "spring.datasource.url", sanitizedUrl)));
    }
}
