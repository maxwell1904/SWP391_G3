package com.swp391.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemQueryService {
    private final DataSource dataSource;

    public SystemQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Backend API is running");
        payload.put("stack", "Spring Boot + JPA");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            payload.put("database", metaData.getDatabaseProductName());
            payload.put("jdbcUrl", metaData.getURL());
        } catch (SQLException exception) {
            payload.put("database", "unknown");
        }
        return payload;
    }
}
