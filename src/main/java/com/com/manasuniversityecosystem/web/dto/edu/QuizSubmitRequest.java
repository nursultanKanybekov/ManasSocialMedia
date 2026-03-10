package com.com.manasuniversityecosystem.web.dto.edu;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class QuizSubmitRequest {

    /** Key: questionId, Value: selected option index (0-based) */
    @NotNull
    private Map<UUID, Integer> answers;
}
