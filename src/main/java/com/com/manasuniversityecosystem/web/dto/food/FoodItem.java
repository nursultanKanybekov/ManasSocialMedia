package com.com.manasuniversityecosystem.web.dto.food;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single dish scraped from info.manas.edu.kg.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodItem {
    private String name;       // e.g. "Ezogelin Çorbası"
    private String imageUrl;   // e.g. "https://info.manas.edu.kg/uploads/..."
    private int    calories;   // e.g. 217
    private String searchUrl;  // Google image search link from the <a href>
}