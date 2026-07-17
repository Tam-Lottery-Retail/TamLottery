package com.mtriet.tamlottery.common.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AivenDatabaseUrlTest {

    @Test
    void convertsAivenServiceUriWithoutCopyingEmbeddedCredentials() {
        String jdbcUrl = AivenDatabaseUrl.toJdbcUrl(
                "mysql://avnadmin:secret@mysql-example.aivencloud.com:12345/defaultdb?ssl-mode=REQUIRED");

        assertThat(jdbcUrl).isEqualTo(
                "jdbc:mysql://mysql-example.aivencloud.com:12345/defaultdb?sslMode=REQUIRED&serverTimezone=UTC");
        assertThat(jdbcUrl).doesNotContain("avnadmin", "secret");
    }

    @Test
    void stripsCredentialsFromJdbcUrlAndAddsMandatoryOptions() {
        assertThat(AivenDatabaseUrl.toJdbcUrl("jdbc:mysql://avnadmin:secret@db.example/defaultdb"))
                .isEqualTo("jdbc:mysql://db.example/defaultdb?sslMode=REQUIRED&serverTimezone=UTC");
    }

    @Test
    void rejectsUnsupportedDatabaseUrls() {
        assertThatThrownBy(() -> AivenDatabaseUrl.toJdbcUrl("postgresql://db.example/defaultdb"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void environmentPostProcessorOverridesDatasourceUrlWithSanitizedValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "mysql://avnadmin:secret@db.example:12345/defaultdb?ssl-mode=REQUIRED");
        environment.setActiveProfiles("aiven");

        new AivenDatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getRequiredProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://db.example:12345/defaultdb?sslMode=REQUIRED&serverTimezone=UTC")
                .doesNotContain("avnadmin", "secret");
        assertThat(environment.getRequiredProperty("DB_URL"))
                .doesNotContain("avnadmin", "secret");
    }
}
