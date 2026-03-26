package com.com.manasuniversityecosystem.web.dto.auth;

import lombok.Builder;
import lombok.Data;

/**
 * Verified teacher data scraped from MABIS (info.manas.edu.kg) after successful authentication.
 * All fields are sourced from the university MABIS portal — teachers cannot self-report them.
 */
@Data
@Builder
public class MabisTeacherInfo {

    /** Full name — from the MABIS profile header */
    private String fullName;

    /** Employee / personnel number — e.g. "01447" (Өздүк номер) */
    private String employeeNumber;

    /** MABIS-derived email — e.g. "01447@manas.edu.kg" */
    private String mabisEmail;

    /** Avatar image URL from MABIS photo storage */
    private String avatarUrl;

    /**
     * Department / Faculty name as it appears on MABIS.
     * Used to auto-assign the teacher to the correct Faculty entity.
     */
    private String departmentName;

    /** MABIS username used to authenticate (same as employeeNumber typically) */
    private String mabisUsername;
}