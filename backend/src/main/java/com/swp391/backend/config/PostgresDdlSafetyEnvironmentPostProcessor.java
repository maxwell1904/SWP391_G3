package com.swp391.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Locale;
import java.util.Set;

/**
 * PostgreSQL/Supabase is owned by Flyway.  Refuse Hibernate modes that can
 * create, alter, or drop that shared schema before JPA gets a connection.
 * Local H2 remains a development fallback; automated tests override it with an
 * isolated in-memory datasource.
 */
public class PostgresDdlSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Set<String> UNSAFE_MODES = Set.of("create", "create-drop", "update");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url", "").toLowerCase(Locale.ROOT);
        String ddlMode = environment.getProperty("spring.jpa.hibernate.ddl-auto", "none").toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:postgresql:") && UNSAFE_MODES.contains(ddlMode)) {
            throw new IllegalStateException(
                    "Refusing Hibernate ddl-auto=" + ddlMode
                            + " against PostgreSQL. Use the postgres profile and Flyway with ddl-auto=validate."
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
