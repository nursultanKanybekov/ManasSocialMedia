package com.com.manasuniversityecosystem.web.dto.auth;

import lombok.Builder;
import lombok.Data;

/**
 * Verified student data scraped from OBIS after successful authentication.
 * All fields are sourced from the university portal — students cannot self-report them.
 */
@Data
@Builder
public class ObisStudentInfo {

    /** Full name — from <h4> in profile card or navbar span */
    private String fullName;

    /** Student number — e.g. "2335.09016" from table#w0 first row */
    private String studentId;

    /** OBIS-assigned email — e.g. "2335.09016@manas.edu.kg" */
    private String obisEmail;

    /** Avatar image URL from OBIS photo storage */
    private String avatarUrl;

    /** Year admitted to university — from "Кабыл алынган жылы: 2023" */
    private Integer admissionYear;

    /**
     * Current year of study (1–6), calculated from admissionYear + academic calendar.
     * CANNOT be faked — derived from OBIS admission year, not user input.
     */
    private Integer studyYear;

    /** Faculty name as it appears on OBIS portal — authoritative source */
    private String facultyName;

    /**
     * Programme duration in years scraped from OBIS (e.g. 4 for bachelor, 2 for master).
     * Used by graduation detection: student graduates when currentAcademicYear > admissionYear + programmeYears.
     * Defaults to 4 if not determinable.
     */
    private Integer programmeYears;

    /** OBIS username used to authenticate */
    private String obisUsername;
}