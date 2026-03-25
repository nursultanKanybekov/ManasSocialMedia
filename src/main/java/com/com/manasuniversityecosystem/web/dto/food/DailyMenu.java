package com.com.manasuniversityecosystem.web.dto.food;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A single day's menu (date label + list of dishes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMenu {
    /** Raw date string as it appears on the page, e.g. "25.03.2026 Çarşamba". */
    private String dateLabel;

    /** The day-of-week portion only, e.g. "Çarşamba". */
    private String dayName;

    /** e.g. "25.03.2026" */
    private String date;

    @Builder.Default
    private List<FoodItem> items = new ArrayList<>();

    /** Total calories for the day. */
    public int totalCalories() {
        return items.stream().mapToInt(FoodItem::getCalories).sum();
    }
}