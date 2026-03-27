package com.com.manasuniversityecosystem.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/timetable")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
public class TimetableController {

    @GetMapping
    public String timetablePage() {
        return "timetable/timetable";
    }
}