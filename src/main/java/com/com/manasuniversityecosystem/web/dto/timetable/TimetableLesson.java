package com.com.manasuniversityecosystem.web.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableLesson {
    private int    id;
    private String group;
    private int    day;
    private int    slot;
    private String subject;
    private String type;
    private String professor;
    private String room;
    private String faculty;
}