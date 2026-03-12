package com.com.manasuniversityecosystem.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[Migration] Running chat_message column migration...");

        String[] migrations = {
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_edited BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_id UUID",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_content VARCHAR(200)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_sender_name VARCHAR(100)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS forwarded_from VARCHAR(100)"
        };

        for (String sql : migrations) {
            try {
                jdbc.execute(sql);
                log.info("[Migration] OK: {}", sql.substring(0, Math.min(60, sql.length())));
            } catch (Exception e) {
                log.warn("[Migration] Skipped ({}): {}", e.getMessage(), sql.substring(0, Math.min(60, sql.length())));
            }
        }

        log.info("[Migration] Done.");
    }
}