package com.com.manasuniversityecosystem.web.dto.profile;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class ProfileUpdateRequest {

    // ── Account fields ──────────────────────────────────────────
    private String fullName;
    private String firstName;
    private String lastName;
    private String gender;
    private String email;
    private UUID   facultyId;
    private Integer graduationYear;

    // ── Role-specific fields ────────────────────────────────────
    /** Mezun: place of work */
    private String workPlace;

    /** Employer: field of operation */
    private String companyField;
    private String companyName;

    // ── Personal / profile ──────────────────────────────────────
    private String bio;
    private String headline;
    private String currentJobTitle;
    private String currentCompany;
    private String phone;
    private String location;
    private String website;
    private String dateOfBirth;
    private String nationality;

    // ── Resume sections (parsed from JSON hidden fields by service) ──
    private String workExperienceJson;
    private String educationJson;
    private String languagesJson;
    private String certificationsJson;

    // ── Simple lists ────────────────────────────────────────────
    private List<String>            skills;
    private Map<String, String>     socialLinks;

    // ── Study info ──────────────────────────────────────────────
    private Integer studyYear;

    // ── Mentorship ──────────────────────────────────────────────
    private Boolean canMentor;
    private String  mentorJobTitle;
}