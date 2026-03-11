package com.com.manasuniversityecosystem.web.dto.profile;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProfileUpdateRequest {

    private String fullName;
    private String bio;
    private String headline;
    private List<String> skills;
    private Map<String, String> socialLinks;
    private Boolean canMentor;
    private String mentorJobTitle;
    private String currentJobTitle;
    private String currentCompany;
}