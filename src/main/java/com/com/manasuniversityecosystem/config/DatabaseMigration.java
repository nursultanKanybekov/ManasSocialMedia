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
                // ── chat_message columns ──────────────────────────────────────
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_edited BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_id UUID",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_content VARCHAR(200)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reply_to_sender_name VARCHAR(100)",
                "ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS forwarded_from VARCHAR(100)",

                // ── Fix point_transaction reason constraint ───────────────────
                "ALTER TABLE point_transaction DROP CONSTRAINT IF EXISTS point_transaction_reason_check",
                "ALTER TABLE point_transaction ADD CONSTRAINT point_transaction_reason_check "
                        + "CHECK (reason IN ('POST','COMMENT','LIKE_RECEIVED','MENTOR','QUIZ','JOB_HELP','LOGIN','QUIZ_PASS','GAME_WIN'))",

                // ── Fix notification type constraint (add PASSWORD_RESET_REQUEST) ──
                "ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check",
                "ALTER TABLE notification ADD CONSTRAINT notification_type_check "
                        + "CHECK (type IN ('ACCOUNT_REGISTERED','ACCOUNT_APPROVED','ACCOUNT_REJECTED','ACCOUNT_SUSPENDED',"
                        + "'ROLE_CHANGED','POST_LIKED','POST_COMMENTED','POST_CREATED','JOB_APPLIED','APPLICATION_STATUS',"
                        + "'JOB_POSTED','MENTORSHIP_REQUESTED','MENTORSHIP_ACCEPTED','MENTORSHIP_DECLINED',"
                        + "'MENTORSHIP_COMPLETED','BADGE_EARNED','SYSTEM','PASSWORD_RESET_REQUEST'))",

                // ── Seed badges ───────────────────────────────────────────────
                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'FIRST_POST', "
                        + "{'en':'First Post','ru':'Первый пост','ky':'Биринчи пост'}::jsonb, "
                        + "{'en':'Published your first post'}::jsonb, 'BRONZE') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'ACTIVE_MEMBER', "
                        + "{'en':'Active Member','ru':'Активный участник','ky':'Активдүү мүчө'}::jsonb, "
                        + "{'en':'Earned 100+ rating points'}::jsonb, 'SILVER') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'MENTOR_STAR', "
                        + "{'en':'Mentor Star','ru':'Звезда ментора','ky':'Ментор Жылдызы'}::jsonb, "
                        + "{'en':'Mentored 3 or more students'}::jsonb, 'GOLD') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'NETWORKER', "
                        + "{'en':'Networker','ru':'Коммуникатор','ky':'Байланышуучу'}::jsonb, "
                        + "{'en':'Posted 50+ comments'}::jsonb, 'SILVER') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'TOP_100', "
                        + "{'en':'Top 100','ru':'Топ 100','ky':'Топ 100'}::jsonb, "
                        + "{'en':'Ranked in the global top 100'}::jsonb, 'GOLD') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'FIRST_GAME_WIN', "
                        + "{'en':'First Victory','ru':'Первая победа','ky':'Биринчи жеңиш'}::jsonb, "
                        + "{'en':'Won your first multiplayer game'}::jsonb, 'BRONZE') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'GAME_CHAMPION', "
                        + "{'en':'Game Champion','ru':'Чемпион игр','ky':'Оюн Чемпиону'}::jsonb, "
                        + "{'en':'Won 10 multiplayer games'}::jsonb, 'PLATINUM') ON CONFLICT (code) DO NOTHING",

                "INSERT INTO badge (id, code, name_i18n, description_i18n, tier) "
                        + "VALUES (gen_random_uuid(), 'QUIZ_MASTER', "
                        + "{'en':'Quiz Master','ru':'Мастер викторины','ky':'Викторина Устасы'}::jsonb, "
                        + "{'en':'Passed 5 or more quizzes'}::jsonb, 'SILVER') ON CONFLICT (code) DO NOTHING"
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