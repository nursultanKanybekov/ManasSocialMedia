package com.com.manasuniversityecosystem.web.dto.career;

import com.com.manasuniversityecosystem.domain.enums.JobType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class CreateJobRequest {

    @NotEmpty
    private Map<String, String> titleI18n;

    @NotEmpty
    private Map<String, String> descriptionI18n;

    @NotNull
    private JobType jobType;

    private String location;
    private String salaryRange;
    private LocalDate deadline;
}