package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.service.FoodScraperService;
import com.com.manasuniversityecosystem.web.dto.food.DailyMenu;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/food")
@PreAuthorize("hasAnyRole('STUDENT','MEZUN','TEACHER','ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Food Menu", description = "Manas University cafeteria daily and weekly menus")
public class FoodController {

    private final FoodScraperService foodScraperService;

    /** Daily menu view — Thymeleaf page */
    @GetMapping
    @Operation(summary = "Daily menu page")
    public String dailyPage(Model model) {
        DailyMenu today  = foodScraperService.fetchToday();
        List<DailyMenu> week = foodScraperService.fetchWeekly();
        model.addAttribute("today", today);
        // Find today's entry in the weekly list to highlight (same date string)
        model.addAttribute("weeklyMenus", week);
        return "food/daily";
    }

    /** Weekly menu view — Thymeleaf page */
    @GetMapping("/weekly")
    @Operation(summary = "Weekly menu page")
    public String weeklyPage(Model model) {
        model.addAttribute("weeklyMenus", foodScraperService.fetchWeekly());
        return "food/weekly";
    }

    // ── REST API (Swagger-documented) ──────────────────────────────────────────

    @GetMapping("/api/today")
    @ResponseBody
    @Operation(summary = "Today's menu as JSON")
    public ResponseEntity<DailyMenu> apiToday() {
        return ResponseEntity.ok(foodScraperService.fetchToday());
    }

    @GetMapping("/api/weekly")
    @ResponseBody
    @Operation(summary = "Full week menu as JSON")
    public ResponseEntity<List<DailyMenu>> apiWeekly() {
        return ResponseEntity.ok(foodScraperService.fetchWeekly());
    }

    @GetMapping("/api/refresh")
    @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Force cache refresh (Admin only)")
    public ResponseEntity<String> refresh() {
        foodScraperService.evictFoodCache();
        return ResponseEntity.ok("{\"status\":\"refreshed\"}");
    }
}