package com.mtriet.tamlottery.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public record DemoDataProperties(
        boolean enabled,
        boolean exitAfterSeed,
        String storeCode,
        String storeName,
        String ownerUsername,
        String ownerPassword,
        String sellerUsername,
        String sellerPassword
) {
}
