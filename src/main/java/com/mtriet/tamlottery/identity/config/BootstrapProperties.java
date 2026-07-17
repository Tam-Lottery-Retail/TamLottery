package com.mtriet.tamlottery.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String storeCode,
        String storeName,
        String ownerUsername,
        String ownerPassword,
        String ownerFullName
) {
}

