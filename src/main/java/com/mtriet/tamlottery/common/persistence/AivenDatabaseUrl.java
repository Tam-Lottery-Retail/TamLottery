package com.mtriet.tamlottery.common.persistence;

import java.net.URI;

public final class AivenDatabaseUrl {

    private AivenDatabaseUrl() {
    }

    public static String toJdbcUrl(String configuredUrl) {
        String value = configuredUrl == null ? "" : configuredUrl.trim();
        if (value.regionMatches(true, 0, "jdbc:mysql://", 0, "jdbc:mysql://".length())) {
            return stripEmbeddedCredentials(withRequiredOptions(value));
        }
        if (!value.regionMatches(true, 0, "mysql://", 0, "mysql://".length())) {
            throw new IllegalArgumentException("DB_URL must use mysql:// or jdbc:mysql://");
        }

        URI serviceUri = URI.create(value);
        String host = serviceUri.getHost();
        String database = serviceUri.getPath() == null ? "" : serviceUri.getPath().replaceFirst("^/", "");
        if (host == null || host.isBlank() || database.isBlank()) {
            throw new IllegalArgumentException("Aiven DB_URL must contain a host and database name");
        }
        String port = serviceUri.getPort() < 0 ? "" : ":" + serviceUri.getPort();
        return "jdbc:mysql://" + host + port + "/" + database + "?sslMode=REQUIRED&serverTimezone=UTC";
    }

    private static String withRequiredOptions(String jdbcUrl) {
        String result = jdbcUrl;
        if (!containsOption(result, "sslMode") && !containsOption(result, "useSSL")) {
            result = appendOption(result, "sslMode=REQUIRED");
        }
        if (!containsOption(result, "serverTimezone")) {
            result = appendOption(result, "serverTimezone=UTC");
        }
        return result;
    }

    private static String stripEmbeddedCredentials(String jdbcUrl) {
        int authorityStart = "jdbc:mysql://".length();
        int authorityEnd = jdbcUrl.indexOf('/', authorityStart);
        int at = jdbcUrl.indexOf('@', authorityStart);
        if (at >= authorityStart && (authorityEnd < 0 || at < authorityEnd)) {
            return jdbcUrl.substring(0, authorityStart) + jdbcUrl.substring(at + 1);
        }
        return jdbcUrl;
    }

    private static boolean containsOption(String url, String name) {
        return url.matches("(?i).*([?&])" + name + "=[^&]*.*");
    }

    private static String appendOption(String url, String option) {
        return url + (url.contains("?") ? "&" : "?") + option;
    }
}
