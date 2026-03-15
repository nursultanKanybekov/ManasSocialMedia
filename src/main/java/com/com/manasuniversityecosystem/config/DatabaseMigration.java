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
        log.info("[Migration] Running database migrations...");

        String[] migrations = {

                // ── chat_message columns ─────────────────────────────────────
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_edited BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_id UUID",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_content VARCHAR(200)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_sender_name VARCHAR(100)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS forwarded_from VARCHAR(100)",

                // ── Fix point_transaction reason CHECK constraint ─────────────
                "ALTER TABLE point_transaction DROP CONSTRAINT IF EXISTS point_transaction_reason_check",
                "ALTER TABLE point_transaction ADD CONSTRAINT point_transaction_reason_check "
                        + "CHECK (reason IN ('POST','COMMENT','LIKE_RECEIVED','MENTOR','QUIZ',"
                        + "'JOB_HELP','LOGIN','QUIZ_PASS','GAME_WIN'))",

                // ── Seed badges (safe: ON CONFLICT DO NOTHING) ───────────────
                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'FIRST_POST',"
                        + "'{\"en\":\"First Post\",\"ru\":\"\u041f\u0435\u0440\u0432\u044b\u0439 \u043f\u043e\u0441\u0442\",\"ky\":\"\u0411\u0438\u0440\u0438\u043d\u0447\u0438 \u043f\u043e\u0441\u0442\"}'::jsonb,"
                        + "'{\"en\":\"Published your first post\"}'::jsonb,"
                        + "'BRONZE') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'ACTIVE_MEMBER',"
                        + "'{\"en\":\"Active Member\",\"ru\":\"\u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0439 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\",\"ky\":\"\u0410\u043a\u0442\u0438\u0432\u0434\u04af\u04af \u043c\u04af\u0447\u04e9\"}'::jsonb,"
                        + "'{\"en\":\"Earned 100+ rating points\"}'::jsonb,"
                        + "'SILVER') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'MENTOR_STAR',"
                        + "'{\"en\":\"Mentor Star\",\"ru\":\"\u0417\u0432\u0435\u0437\u0434\u0430 \u043c\u0435\u043d\u0442\u043e\u0440\u0430\",\"ky\":\"\u041c\u0435\u043d\u0442\u043e\u0440 \u0416\u044b\u043b\u0434\u044b\u0437\u044b\"}'::jsonb,"
                        + "'{\"en\":\"Mentored 3 or more students\"}'::jsonb,"
                        + "'GOLD') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'NETWORKER',"
                        + "'{\"en\":\"Networker\",\"ru\":\"\u041a\u043e\u043c\u043c\u0443\u043d\u0438\u043a\u0430\u0442\u043e\u0440\",\"ky\":\"\u0411\u0430\u0439\u043b\u0430\u043d\u044b\u0448\u0443\u0443\u0447\u0443\"}'::jsonb,"
                        + "'{\"en\":\"Posted 50+ comments\"}'::jsonb,"
                        + "'SILVER') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'TOP_100',"
                        + "'{\"en\":\"Top 100\",\"ru\":\"\u0422\u043e\u043f 100\",\"ky\":\"\u0422\u043e\u043f 100\"}'::jsonb,"
                        + "'{\"en\":\"Ranked in the global top 100\"}'::jsonb,"
                        + "'GOLD') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'FIRST_GAME_WIN',"
                        + "'{\"en\":\"First Victory\",\"ru\":\"\u041f\u0435\u0440\u0432\u0430\u044f \u043f\u043e\u0431\u0435\u0434\u0430\",\"ky\":\"\u0411\u0438\u0440\u0438\u043d\u0447\u0438 \u0436\u0435\u04a3\u0438\u0448\"}'::jsonb,"
                        + "'{\"en\":\"Won your first multiplayer game\"}'::jsonb,"
                        + "'BRONZE') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'GAME_CHAMPION',"
                        + "'{\"en\":\"Game Champion\",\"ru\":\"\u0427\u0435\u043c\u043f\u0438\u043e\u043d \u0438\u0433\u0440\",\"ky\":\"\u041e\u044e\u043d \u0427\u0435\u043c\u043f\u0438\u043e\u043d\u0443\"}'::jsonb,"
                        + "'{\"en\":\"Won 10 multiplayer games\"}'::jsonb,"
                        + "'PLATINUM') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) VALUES ("
                        + "gen_random_uuid(), 'QUIZ_MASTER',"
                        + "'{\"en\":\"Quiz Master\",\"ru\":\"\u041c\u0430\u0441\u0442\u0435\u0440 \u0432\u0438\u043a\u0442\u043e\u0440\u0438\u043d\u044b\",\"ky\":\"\u0412\u0438\u043a\u0442\u043e\u0440\u0438\u043d\u0430 \u0423\u0441\u0442\u0430\u0441\u044b\"}'::jsonb,"
                        + "'{\"en\":\"Passed 5 or more quizzes\"}'::jsonb,"
                        + "'SILVER') ON CONFLICT (code) DO NOTHING"
        };

        for (String sql : migrations) {
            try {
                jdbc.execute(sql);
                log.info("[Migration] OK: {}", sql.substring(0, Math.min(60, sql.length())));
            } catch (Exception e) {
                log.warn("[Migration] Skipped ({}): {}", e.getMessage(),
                        sql.substring(0, Math.min(60, sql.length())));
            }
        }

        log.info("[Migration] Done.");
    }
}