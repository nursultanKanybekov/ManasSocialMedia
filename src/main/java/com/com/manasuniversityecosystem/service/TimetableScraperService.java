package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.web.dto.timetable.TimetableLesson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.htmlunit.*;
import org.htmlunit.html.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Scrapes timetable.manas.edu.kg using HtmlUnit — a headless browser that
 * executes JavaScript, so dynamically rendered timetable tables are captured.
 *
 * Pages:
 *   /teacher    — ~795 options
 *   /department — ~85  options
 *   /room       — ~459 options
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableScraperService {

    private static final String BASE           = "http://timetable.manas.edu.kg";
    private static final String URL_TEACHER    = BASE + "/teacher";
    private static final String URL_DEPARTMENT = BASE + "/department";
    private static final String URL_ROOM       = BASE + "/room";
    private static final String F              = "timetablebundle_dersplangenerator";

    private static final String[] DAYS_TR    = {"Pazartesi","Sal\u0131","\u00c7ar\u015famba","Per\u015fembe","Cuma","Cumartesi"};
    private static final String[] DAYS_EN    = {"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
    private static final String[] DAYS_RU    = {"\u041f\u043e\u043d\u0435\u0434\u0435\u043b\u044c\u043d\u0438\u043a","\u0412\u0442\u043e\u0440\u043d\u0438\u043a","\u0421\u0440\u0435\u0434\u0430","\u0427\u0435\u0442\u0432\u0435\u0440\u0433","\u041f\u044f\u0442\u043d\u0438\u0446\u0430","\u0421\u0443\u0431\u0431\u043e\u0442\u0430"};
    private static final String[] SLOT_STARTS = {"08:00","09:40","11:20","13:40","15:20","17:00"};

    private final CacheManager cacheManager;

    private final AtomicBoolean scraping   = new AtomicBoolean(false);
    private volatile boolean    cacheReady = false;

    // ── Public API ──────────────────────────────────────────────────────────

    @Cacheable(value = "timetable", key = "'all'")
    public List<TimetableLesson> fetchAll() {
        log.info("[Timetable] Cache miss — scraping on calling thread");
        return doScrapeAll();
    }

    public List<TimetableLesson> fetchByGroup(String g) {
        if (g==null||g.isBlank()) return fetchAll();
        String q = g.trim().toUpperCase();
        return fetchAll().stream().filter(l -> l.getGroup()!=null && l.getGroup().toUpperCase().startsWith(q)).toList();
    }
    public List<TimetableLesson> fetchByProfessor(String n) {
        if (n==null||n.isBlank()) return fetchAll();
        String q = n.trim().toLowerCase();
        return fetchAll().stream().filter(l -> l.getProfessor()!=null && l.getProfessor().toLowerCase().contains(q)).toList();
    }
    public List<TimetableLesson> fetchByRoom(String r) {
        if (r==null||r.isBlank()) return fetchAll();
        String q = r.trim().toLowerCase();
        return fetchAll().stream().filter(l -> l.getRoom()!=null && l.getRoom().toLowerCase().contains(q)).toList();
    }
    public List<TimetableLesson> fetchByFaculty(String f) {
        if (f==null||f.isBlank()) return fetchAll();
        String q = f.trim().toLowerCase();
        return fetchAll().stream().filter(l -> l.getFaculty()!=null && l.getFaculty().toLowerCase().contains(q)).toList();
    }

    public boolean isCacheReady() { return cacheReady; }
    public boolean isScraping()   { return scraping.get(); }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheAsync() {
        if (!scraping.compareAndSet(false, true)) {
            log.info("[Timetable] Warm-up skipped — already running");
            return;
        }
        try {
            log.info("[Timetable] Background cache warm-up starting…");
            List<TimetableLesson> result = doScrapeAll();
            Cache cache = cacheManager.getCache("timetable");
            if (cache != null) {
                cache.put("all", result);
                log.info("[Timetable] Cached {} lessons", result.size());
            }
            cacheReady = true;
        } catch (Exception ex) {
            log.error("[Timetable] Warm-up failed: {}", ex.getMessage(), ex);
        } finally {
            scraping.set(false);
        }
    }

    @CacheEvict(value = "timetable", allEntries = true)
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void evictCache() {
        log.info("[Timetable] Cache evicted");
        cacheReady = false;
        warmCacheAsync();
    }

    // ── Core scrape ─────────────────────────────────────────────────────────

    private List<TimetableLesson> doScrapeAll() {
        Map<String, TimetableLesson> merged = new LinkedHashMap<>();
        AtomicInteger id = new AtomicInteger(1);

        // Silence HtmlUnit's verbose CSS/JS warning logs
        silenceHtmlUnitLogs();

        try (WebClient webClient = buildWebClient()) {
            scrapeFormPage(webClient, URL_DEPARTMENT, "department", merged, id);
            scrapeFormPage(webClient, URL_TEACHER,    "teacher",    merged, id);
            scrapeFormPage(webClient, URL_ROOM,       "room",       merged, id);
        } catch (Exception e) {
            log.error("[Timetable] WebClient error: {}", e.getMessage(), e);
        }

        List<TimetableLesson> result = new ArrayList<>(merged.values());
        log.info("[Timetable] Scrape complete — {} lessons", result.size());
        return result;
    }

    private WebClient buildWebClient() {
        WebClient wc = new WebClient(BrowserVersion.CHROME);
        wc.getOptions().setJavaScriptEnabled(true);
        wc.getOptions().setCssEnabled(false);           // we don't need CSS
        wc.getOptions().setThrowExceptionOnScriptError(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setPrintContentOnFailingStatusCode(false);
        wc.getOptions().setTimeout(25_000);
        wc.setAjaxController(new NicelyResynchronizingAjaxController());
        wc.waitForBackgroundJavaScript(3_000);          // wait up to 3s for JS
        return wc;
    }

    private void scrapeFormPage(WebClient wc, String pageUrl, String entityType,
                                Map<String, TimetableLesson> merged, AtomicInteger id) {
        log.info("[Timetable] Scraping {} — {}", entityType, pageUrl);
        try {
            HtmlPage page = wc.getPage(pageUrl);
            wc.waitForBackgroundJavaScript(2_000);

            // Find the select element
            String selectId   = F + "_" + entityType;
            String selectName = F + "[" + entityType + "]";

            HtmlSelect select = null;
            try { select = page.getHtmlElementById(selectId); }
            catch (Exception ignored) {}

            if (select == null) {
                try { select = (HtmlSelect) page.getFirstByXPath("//select[@name='" + selectName + "']"); }
                catch (Exception ignored) {}
            }

            if (select == null) {
                log.warn("[Timetable] Select not found on {}", pageUrl);
                return;
            }

            List<HtmlOption> options = select.getOptions().stream()
                    .filter(o -> !o.getValueAttribute().isEmpty() && !o.getValueAttribute().equals("0"))
                    .toList();

            log.info("[Timetable] {} options on {}", options.size(), entityType);
            if (options.isEmpty()) return;

            int lessonsBefore = merged.size();
            int count = 0;

            for (HtmlOption opt : options) {
                String optVal  = opt.getValueAttribute().trim();
                String optText = opt.getVisibleText().trim();
                if (optVal.isEmpty() || optVal.equals("0")) continue;

                try {
                    Thread.sleep(120);

                    // Select the option — this triggers onChange JS if any
                    HtmlPage current = wc.getPage(pageUrl);
                    wc.waitForBackgroundJavaScript(1_000);

                    HtmlSelect sel2 = null;
                    try { sel2 = current.getHtmlElementById(selectId); }
                    catch (Exception ignored) {}
                    if (sel2 == null) {
                        try { sel2 = (HtmlSelect) current.getFirstByXPath("//select[@name='" + selectName + "']"); }
                        catch (Exception ignored) {}
                    }
                    if (sel2 == null) continue;

                    sel2.setSelectedAttribute(optVal, true);

                    // Submit the form
                    HtmlForm form = null;
                    try { form = current.getFormByName(F); }
                    catch (Exception ignored) {}

                    HtmlPage result;
                    if (form != null) {
                        // Try submit button first, fall back to form submit
                        HtmlElement submitBtn = null;
                        try { submitBtn = form.getOneHtmlElementByAttribute("button", "type", "submit"); }
                        catch (Exception ignored) {}
                        if (submitBtn == null) {
                            try { submitBtn = form.getOneHtmlElementByAttribute("input", "type", "submit"); }
                            catch (Exception ignored) {}
                        }

                        if (submitBtn != null) {
                            result = submitBtn.click();
                        } else {
                            result = current; // form submission via JS
                        }
                    } else {
                        result = current;
                    }

                    wc.waitForBackgroundJavaScript(3_000);

                    // Get fully rendered HTML and parse with Jsoup
                    String renderedHtml = result.asXml();
                    Document doc = Jsoup.parse(renderedHtml);
                    List<TimetableLesson> lessons = parseScheduleDoc(doc, entityType, optText, id);

                    if (!lessons.isEmpty()) {
                        mergeInto(merged, lessons);
                        count++;
                        log.debug("[Timetable] {} '{}' → {} lessons", entityType, optText, lessons.size());
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.debug("[Timetable] Error on {} '{}': {}", entityType, optText, ex.getMessage());
                }
            }

            log.info("[Timetable] {} done — {} successful, {} new lessons total",
                    entityType, count, merged.size() - lessonsBefore);

        } catch (Exception e) {
            log.error("[Timetable] {} page error: {}", pageUrl, e.getMessage(), e);
        }
    }

    // ── Parse rendered HTML ─────────────────────────────────────────────────

    private List<TimetableLesson> parseScheduleDoc(Document doc, String entityType,
                                                   String entityName, AtomicInteger id) {
        List<TimetableLesson> lessons = new ArrayList<>();

        for (Element table : doc.select("table")) {
            Elements rows = table.select("tr");
            if (rows.isEmpty()) continue;

            Element headerRow = table.selectFirst("thead tr");
            if (headerRow == null) headerRow = rows.first();

            ColMap cols = detectCols(headerRow);
            if (cols.day < 0 && cols.time < 0) continue;

            Elements dataRows = table.select("tbody tr");
            if (dataRows.isEmpty()) dataRows = new Elements(rows.subList(1, rows.size()));

            for (Element row : dataRows) {
                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;
                TimetableLesson l = parseRow(cells, cols, entityType, entityName, id);
                if (l != null) lessons.add(l);
            }
            if (!lessons.isEmpty()) return lessons;
        }
        return lessons;
    }

    private TimetableLesson parseRow(Elements cells, ColMap cols,
                                     String entityType, String entityName, AtomicInteger id) {
        String dayText   = cell(cells, cols.day);
        String timeText  = cell(cells, cols.time);
        String subject   = cell(cells, cols.subject);
        String professor = cell(cells, cols.professor);
        String room      = cell(cells, cols.room);
        String group     = cell(cells, cols.group);
        String faculty   = cell(cells, cols.faculty);

        if (blank(subject)) return null;
        int day  = parseDayIndex(dayText);  if (day < 0) return null;
        int slot = parseSlotIndex(timeText); if (slot < 0) slot = 0;

        switch (entityType) {
            case "teacher"    -> { if (blank(professor)) professor = entityName; }
            case "room"       -> { if (blank(room))      room      = entityName; }
            case "department" -> { if (blank(faculty))   faculty   = entityName; }
        }

        return TimetableLesson.builder()
                .id(id.getAndIncrement())
                .group(blank(group) ? "?" : group)
                .day(day).slot(slot)
                .subject(subject)
                .type(detectType(subject))
                .professor(nvl(professor, ""))
                .room(nvl(room, ""))
                .faculty(nvl(faculty, ""))
                .build();
    }

    private ColMap detectCols(Element headerRow) {
        ColMap c = new ColMap();
        if (headerRow == null) return c;
        Elements cells = headerRow.select("th, td");
        for (int i = 0; i < cells.size(); i++) {
            String h = cells.get(i).text().toLowerCase().trim();
            if (h.contains("g\u00fcn")    || h.contains("day")     || h.contains("hafta"))           c.day       = i;
            if (h.contains("saat")        || h.contains("time")    || h.contains("zaman"))           c.time      = i;
            if ((h.contains("ders") && !h.contains("derslik")) || h.contains("konu") ||
                    h.contains("lesson")  || h.contains("subject")  || h.contains("course"))         c.subject   = i;
            if (h.contains("\u00f6\u011fret") || h.contains("hoca") || h.contains("teacher") ||
                    h.contains("instructor")  || h.contains("prof"))                                 c.professor = i;
            if (h.contains("derslik")     || h.contains("room")    || h.contains("s\u0131n\u0131f") ||
                    h.contains("salon"))                                                              c.room      = i;
            if (h.contains("grup")        || h.contains("group")   || h.contains("sinif") ||
                    h.contains("\u00f6\u011frenci"))                                                  c.group     = i;
            if (h.contains("b\u00f6l\u00fcm") || h.contains("fak\u00fcl") || h.contains("dept"))    c.faculty   = i;
        }
        return c;
    }

    private void mergeInto(Map<String, TimetableLesson> map, List<TimetableLesson> newLessons) {
        for (TimetableLesson l : newLessons) {
            String sub = nvl(l.getSubject(), "?");
            String key = l.getDay() + "-" + l.getSlot() + "-" + nvl(l.getGroup(),"?") + "-"
                    + sub.substring(0, Math.min(30, sub.length()));
            TimetableLesson ex = map.get(key);
            if (ex == null) {
                map.put(key, l);
            } else {
                if (blank(ex.getProfessor()) && !blank(l.getProfessor())) ex.setProfessor(l.getProfessor());
                if (blank(ex.getRoom())      && !blank(l.getRoom()))      ex.setRoom(l.getRoom());
                if (blank(ex.getFaculty())   && !blank(l.getFaculty()))   ex.setFaculty(l.getFaculty());
                if (blank(ex.getGroup())     && !blank(l.getGroup()))     ex.setGroup(l.getGroup());
            }
        }
    }

    private static class ColMap { int day=-1,time=-1,subject=-1,professor=-1,room=-1,group=-1,faculty=-1; }

    private void silenceHtmlUnitLogs() {
        java.util.logging.Logger.getLogger("org.htmlunit").setLevel(Level.WARNING);
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.WARNING);
        java.util.logging.Logger.getLogger("org.apache.http").setLevel(Level.WARNING);
    }

    private int parseDayIndex(String text) {
        if (text==null) return -1;
        String t = text.trim();
        try { int n=Integer.parseInt(t); return (n>=0&&n<=5)?n:(n>=1&&n<=6)?n-1:-1; } catch(Exception ignored){}
        for (int i=0; i<DAYS_TR.length; i++)
            if (t.equalsIgnoreCase(DAYS_TR[i])||t.equalsIgnoreCase(DAYS_EN[i])||t.equalsIgnoreCase(DAYS_RU[i])) return i;
        String lower = t.toLowerCase();
        if (lower.startsWith("paz")||lower.startsWith("mon")||lower.startsWith("\u043f\u043d")) return 0;
        if (lower.startsWith("sal")||lower.startsWith("tue")||lower.startsWith("\u0432\u0442")) return 1;
        if (lower.startsWith("\u00e7ar")||lower.startsWith("wed")||lower.startsWith("\u0441\u0440")) return 2;
        if (lower.startsWith("per")||lower.startsWith("thu")||lower.startsWith("\u0447\u0442")) return 3;
        if (lower.startsWith("cum")||lower.startsWith("fri")||lower.startsWith("\u043f\u0442")) return 4;
        if (lower.startsWith("cmt")||lower.startsWith("sat")||lower.startsWith("\u0441\u0431")) return 5;
        return -1;
    }

    private int parseSlotIndex(String t) {
        if (t==null||t.isBlank()) return -1;
        try { int n=Integer.parseInt(t.trim()); return (n>=0&&n<=5)?n:(n>=1&&n<=6)?n-1:-1; } catch(Exception ignored){}
        for (int i=0; i<SLOT_STARTS.length; i++) if (t.startsWith(SLOT_STARTS[i])) return i;
        String f = t.split("[-\u2013\\s]")[0].trim();
        for (int i=0; i<SLOT_STARTS.length; i++) if (f.equals(SLOT_STARTS[i])) return i;
        return -1;
    }

    private String detectType(String text) {
        if (text==null) return "lecture";
        String l = text.toLowerCase();
        if (l.contains("lab")||l.contains("\u043b\u0430\u0431")||l.contains("pratik"))             return "lab";
        if (l.contains("seminar")||l.contains("\u0441\u0435\u043c\u0438\u043d\u0430\u0440")||l.contains("uygulama")) return "seminar";
        return "lecture";
    }

    private String cell(Elements cells, int idx) { return (idx>=0&&idx<cells.size())?cells.get(idx).text().trim():""; }
    private boolean blank(String s) { return s==null||s.isBlank(); }
    private String nvl(String s, String def) { return blank(s)?def:s; }
}