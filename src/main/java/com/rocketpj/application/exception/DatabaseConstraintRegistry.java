package com.rocketpj.application.exception;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Component
public class DatabaseConstraintRegistry {

    private final JdbcTemplate jdbcTemplate;
    private Map<String, String> constraintFieldMap = Collections.emptyMap();

    DatabaseConstraintRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        var sql = """
            SELECT
                tc.CONSTRAINT_NAME AS constraint_name,
                kcu.COLUMN_NAME AS column_name
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
            JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
              ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
              AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
            WHERE tc.CONSTRAINT_TYPE IN ('UNIQUE', 'PRIMARY KEY')
              AND tc.TABLE_SCHEMA = DATABASE()
            """;

        var map = new HashMap<String, String>();

        jdbcTemplate.query(sql, rs -> {
            var constraint = rs.getString("constraint_name");
            var column = rs.getString("column_name");
            map.put(constraint, column);
        });

        this.constraintFieldMap = Collections.unmodifiableMap(map);

        log.info("Database constraint registry initialized with {} entries", constraintFieldMap.size());
    }

    public  String getFieldByConstraint(String constraintName) {
        return constraintFieldMap.getOrDefault(constraintName, "unknown");
    }

    Map<String, String> getConstraintFieldMap() {
        return constraintFieldMap;
    }
}
