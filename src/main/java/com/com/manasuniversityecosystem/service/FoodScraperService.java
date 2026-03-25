package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.web.dto.food.DailyMenu;
import com.com.manasuniversityecosystem.web.dto.food.FoodItem;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scrapes the Manas University cafeteria website (info.manas.edu.kg).
 *
 * <p><b>Source structure:</b>
 * <ul>
 *   <li>{@code https://info.manas.edu.kg/} — Home page with <em>today's</em> menu
 *       inside {@code section#features4-18 .row.mt-4 .item.features-image}</li>
 *   <li>{@code https://info.manas.edu.kg/menu} — Full weekly menu:
 *       multiple {@code .mbr-section-subtitle} day headers followed by
 *       {@code .row.mt-2 .item.features-image} dish rows.</li>
 * </ul>
 *
 * <p>Each dish card has:
 * <pre>
 *   .item-img img            → image URL
 *   .item-title a[href]      → Google search link (searchUrl)
 *   .item-title a strong     → dish name
 *   .item-subtitle           → "Kalori: 217"
 * </pre>
 *
 * <p>Caches are refreshed every 6 hours automatically.
 */
@Slf4j
@Service
public class FoodScraperService {

    private static final String BASE_URL   = "https://beslenme.manas.edu.kg";
    private static final String DAILY_URL  = BASE_URL + "/";
    private static final String WEEKLY_URL = BASE_URL + "/menu";
    private static final int    TIMEOUT_MS = 12_000;
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns today's menu (4 dishes) scraped from the home page.
     * Result is cached; evicted every 6 hours.
     */
    @Cacheable(value = "food-today", key = "'today'")
    public DailyMenu fetchToday() {
        log.info("[Food] Scraping today's menu from {}", DAILY_URL);
        try {
            Document doc = Jsoup.connect(DAILY_URL)
                    .userAgent(UA).timeout(TIMEOUT_MS).get();

            // The home page has one day section: section#features4-18
            Element section = doc.selectFirst("section#features4-18");
            if (section == null) section = doc;

            String dateLabel = extractDateLabel(section);
            List<FoodItem> items = extractItems(section.select(".item.features-image"));

            return DailyMenu.builder()
                    .dateLabel(dateLabel)
                    .date(parseDatePart(dateLabel))
                    .dayName(parseDayPart(dateLabel))
                    .items(items)
                    .build();

        } catch (Exception e) {
            log.error("[Food] Failed to scrape today's menu: {}", e.getMessage(), e);
            return DailyMenu.builder().dateLabel("").items(Collections.emptyList()).build();
        }
    }

    /**
     * Returns the full week menu scraped from /menu.
     * Result is cached; evicted every 6 hours.
     */
    @Cacheable(value = "food-weekly", key = "'weekly'")
    public List<DailyMenu> fetchWeekly() {
        log.info("[Food] Scraping weekly menu from {}", WEEKLY_URL);
        try {
            Document doc = Jsoup.connect(WEEKLY_URL)
                    .userAgent(UA).timeout(TIMEOUT_MS).get();

            Element section = doc.selectFirst("section#features4-18");
            if (section == null) section = doc.body();

            return parseWeeklySection(section);

        } catch (Exception e) {
            log.error("[Food] Failed to scrape weekly menu: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── Scheduled cache eviction ───────────────────────────────────────────────

    @CacheEvict(value = {"food-today", "food-weekly"}, allEntries = true)
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void evictFoodCache() {
        log.info("[Food] Cache evicted — next request will re-scrape");
    }

    // ── Parsing helpers ────────────────────────────────────────────────────────

    /**
     * Parses a section that contains one or more day blocks.
     * Each block is: {@code .mbr-section-head h5.mbr-section-subtitle}
     * followed by {@code .row.mt-2} with dish items.
     *
     * <p>Strategy: walk all children of the section; when we meet a subtitle
     * element (date header), start a new DailyMenu; when we meet a row of
     * items, attach them to the current DailyMenu.
     */
    private List<DailyMenu> parseWeeklySection(Element section) {
        List<DailyMenu> result = new ArrayList<>();
        DailyMenu current = null;

        for (Element child : section.children()) {
            // Day header block
            Element subtitle = child.selectFirst("h5.mbr-section-subtitle");
            if (subtitle != null) {
                String label = subtitle.text().trim();
                if (!label.isBlank()) {
                    current = DailyMenu.builder()
                            .dateLabel(label)
                            .date(parseDatePart(label))
                            .dayName(parseDayPart(label))
                            .items(new ArrayList<>())
                            .build();
                    result.add(current);
                }
                continue;
            }

            // Dish row
            Elements dishes = child.select(".item.features-image");
            if (!dishes.isEmpty() && current != null) {
                current.getItems().addAll(extractItems(dishes));
            }
        }

        // Remove any days with no dishes (parse artifacts)
        result.removeIf(d -> d.getItems().isEmpty());
        return result;
    }

    /**
     * Extracts {@link FoodItem} list from a set of {@code .item.features-image} elements.
     */
    private List<FoodItem> extractItems(Elements elements) {
        List<FoodItem> items = new ArrayList<>();
        for (Element el : elements) {
            FoodItem item = parseItem(el);
            if (item != null) items.add(item);
        }
        return items;
    }

    /**
     * Parses one {@code .item.features-image} card into a {@link FoodItem}.
     * Returns {@code null} if the name cannot be determined.
     */
    private FoodItem parseItem(Element el) {
        try {
            // Image
            Element img = el.selectFirst(".item-img img");
            String imageUrl = img != null ? absoluteUrl(img.attr("src")) : "";

            // Name & search link
            Element titleLink = el.selectFirst(".item-title a");
            String searchUrl = titleLink != null ? titleLink.attr("href") : "";
            Element nameEl   = el.selectFirst(".item-title a strong");
            String name      = nameEl != null ? nameEl.text().trim() : "";
            if (name.isBlank()) return null;

            // Calories: "Kalori: 217"
            Element calEl = el.selectFirst(".item-subtitle");
            int calories  = parseCalories(calEl != null ? calEl.text() : "");

            return FoodItem.builder()
                    .name(name)
                    .imageUrl(imageUrl)
                    .calories(calories)
                    .searchUrl(searchUrl)
                    .build();

        } catch (Exception e) {
            log.debug("[Food] Failed to parse item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the date-label string from the first
     * {@code h5.mbr-section-subtitle} inside the given element.
     */
    private String extractDateLabel(Element section) {
        Element sub = section.selectFirst("h5.mbr-section-subtitle, .mbr-section-subtitle");
        return sub != null ? sub.text().trim() : "";
    }

    /**
     * Parses calories from text like "Kalori: 217".
     * Returns 0 if not found or malformed.
     */
    private int parseCalories(String text) {
        if (text == null || text.isBlank()) return 0;
        try {
            // Remove all non-digits, keep the number
            String digits = text.replaceAll("[^0-9]", "").trim();
            return digits.isBlank() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * "25.03.2026 Çarşamba" → "25.03.2026"
     */
    private String parseDatePart(String label) {
        if (label == null || label.isBlank()) return "";
        return label.split("\\s+")[0].trim();
    }

    /**
     * "25.03.2026 Çarşamba" → "Çarşamba"
     */
    private String parseDayPart(String label) {
        if (label == null || label.isBlank()) return "";
        String[] parts = label.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : "";
    }

    /**
     * Ensures an image URL is absolute (prepends BASE_URL if it starts with /).
     */
    private String absoluteUrl(String url) {
        if (url == null || url.isBlank()) return "";
        if (url.startsWith("http")) return url;
        return url.startsWith("/") ? BASE_URL + url : url;
    }
}