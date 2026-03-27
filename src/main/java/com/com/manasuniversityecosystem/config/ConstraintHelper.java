package com.com.manasuniversityecosystem.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated helper for DDL constraint fixes.
 * Uses REQUIRES_NEW so its transaction is fully independent from the caller —
 * success or failure here never affects DataInitializer's other operations.
 */
@Component
@Slf4j
public class ConstraintHelper {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fixRoleConstraint() {
        try {
            // Step 1: Drop the old constraint (IF EXISTS — safe to run multiple times)
            entityManager.createNativeQuery(
                    "ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_role_check"
            ).executeUpdate();

            // Step 2: Re-add the constraint with ALL roles defined in UserRole enum:
            //   STUDENT, TEACHER, MEZUN, EMPLOYER, ADMIN, SECRETARY, SUPER_ADMIN
            // NOTE: TEACHER was missing from the previous constraint, causing the violation.
            entityManager.createNativeQuery(
                    "ALTER TABLE app_user ADD CONSTRAINT app_user_role_check " +
                            "CHECK (role IN ('STUDENT','TEACHER','MEZUN','EMPLOYER','ADMIN','SECRETARY','SUPER_ADMIN'))"
            ).executeUpdate();

            log.info("✅ Role constraint updated successfully (includes TEACHER + SUPER_ADMIN)");
        } catch (Exception e) {
            log.warn("Could not update role constraint: {}", e.getMessage());
        }
    }
}