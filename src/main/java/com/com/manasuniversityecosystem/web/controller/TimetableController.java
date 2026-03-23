package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.service.TimetableScraperService;
import com.com.manasuniversityecosystem.web.dto.timetable.TimetableLesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/timetable")
@PreAuthorize("hasAnyRole('STUDENT','MEZUN','ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableScraperService scraperService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String timetablePage(Model model) {
        if (scraperService.isCacheReady()) {
            List<TimetableLesson> lessons = scraperService.fetchAll();
            Set<String> faculties  = lessons.stream().map(TimetableLesson::getFaculty)
                    .filter(f -> f != null && !f.isBlank()).collect(Collectors.toCollection(TreeSet::new));
            Set<String> groups     = lessons.stream().map(TimetableLesson::getGroup)
                    .filter(g -> g != null && !g.isBlank()).collect(Collectors.toCollection(TreeSet::new));
            Set<String> professors = lessons.stream().map(TimetableLesson::getProfessor)
                    .filter(p -> p != null && !p.isBlank()).collect(Collectors.toCollection(TreeSet::new));
            Set<String> rooms      = lessons.stream().map(TimetableLesson::getRoom)
                    .filter(r -> r != null && !r.isBlank()).collect(Collectors.toCollection(TreeSet::new));
            model.addAttribute("lessonsJson",    toJson(lessons));
            model.addAttribute("facultiesJson",  toJson(faculties));
            model.addAttribute("groupsJson",     toJson(groups));
            model.addAttribute("professorsJson", toJson(professors));
            model.addAttribute("roomsJson",      toJson(rooms));
            model.addAttribute("lessonCount",    lessons.size());
        } else {
            model.addAttribute("lessonsJson",    "[]");
            model.addAttribute("facultiesJson",  "[]");
            model.addAttribute("groupsJson",     "[]");
            model.addAttribute("professorsJson", "[]");
            model.addAttribute("roomsJson",      "[]");
            model.addAttribute("lessonCount",    0);
        }
        model.addAttribute("cacheReady", scraperService.isCacheReady());
        model.addAttribute("scraping",   scraperService.isScraping());
        model.addAttribute("sourceLabel","timetable.manas.edu.kg (/teacher · /department · /room)");
        return "timetable/timetable";
    }

    @GetMapping("/api/all")    @ResponseBody
    public List<TimetableLesson> apiAll() { return scraperService.fetchAll(); }

    @GetMapping("/api/status") @ResponseBody
    public Map<String,Object> apiStatus() {
        return Map.of("ready", scraperService.isCacheReady(), "scraping", scraperService.isScraping());
    }

    @GetMapping("/api/search") @ResponseBody
    public List<TimetableLesson> apiSearch(
            @RequestParam(required=false) String group,
            @RequestParam(required=false) String professor,
            @RequestParam(required=false) String room,
            @RequestParam(required=false) String faculty) {
        if (group     != null && !group.isBlank())     return scraperService.fetchByGroup(group);
        if (professor != null && !professor.isBlank()) return scraperService.fetchByProfessor(professor);
        if (room      != null && !room.isBlank())      return scraperService.fetchByRoom(room);
        if (faculty   != null && !faculty.isBlank())   return scraperService.fetchByFaculty(faculty);
        return scraperService.fetchAll();
    }

    @GetMapping("/api/refresh") @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public String refresh() { scraperService.evictCache(); return "{\"status\":\"refreshed\"}"; }

    /**
     * DIAGNOSTIC — shows exactly what the site returns for one POST.
     * Open: /timetable/api/raw-post?page=department&optionIndex=0
     * This returns the FULL raw HTML so you can see the timetable structure.
     */
    @GetMapping("/api/raw-post") @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String,Object> rawPost(
            @RequestParam(defaultValue = "department") String page,
            @RequestParam(defaultValue = "0") int optionIndex) throws Exception {

        Map<String,Object> result = new LinkedHashMap<>();
        String url = "http://timetable.manas.edu.kg/" + page;
        String F   = "timetablebundle_dersplangenerator";
        String UA  = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120";

        // GET
        Connection.Response getResp = Jsoup.connect(url)
                .userAgent(UA).timeout(20_000).followRedirects(true)
                .method(Connection.Method.GET).execute();

        result.put("getStatus",    getResp.statusCode());
        result.put("finalUrl",     getResp.url().toString());
        result.put("contentType",  getResp.contentType());

        Document getDoc = getResp.parse();
        result.put("pageTitle",    getDoc.title());

        // CSRF
        String csrf = "";
        Element tok = getDoc.selectFirst("input[name=" + F + "[_token]]");
        if (tok == null) tok = getDoc.selectFirst("input[type=hidden][name$=_token]");
        if (tok != null) csrf = tok.attr("value");
        result.put("csrfFound",    !csrf.isEmpty());

        // Select options
        Element sel = getDoc.selectFirst("select[name=" + F + "[" + page + "]]");
        if (sel == null) {
            result.put("error", "Select not found");
            result.put("allSelects", getDoc.select("select").stream()
                    .map(s -> s.attr("name") + " / " + s.attr("id") + " (" + s.select("option").size() + " opts)").toList());
            return result;
        }

        var opts = sel.select("option").stream()
                .filter(o -> !o.attr("value").isEmpty() && !o.attr("value").equals("0"))
                .toList();

        result.put("optionCount", opts.size());
        if (opts.isEmpty()) { result.put("error", "no valid options"); return result; }

        int idx = Math.min(optionIndex, opts.size()-1);
        String optVal  = opts.get(idx).attr("value");
        String optText = opts.get(idx).text();
        result.put("usingOption", Map.of("index", idx, "value", optVal, "text", optText));

        // POST
        Map<String,String> cookies = new HashMap<>(getResp.cookies());
        Connection.Response postResp = Jsoup.connect(url)
                .userAgent(UA).timeout(20_000).followRedirects(true).cookies(cookies)
                .data(F + "[_token]", csrf)
                .data(F + "[" + page + "]", optVal)
                // NOTE: do NOT send [submit] — Symfony rejects it as "extra field"
                .method(Connection.Method.POST).execute();

        result.put("postStatus",      postResp.statusCode());
        result.put("postFinalUrl",    postResp.url().toString());
        result.put("postContentType", postResp.contentType());

        String body = postResp.body();
        result.put("bodyLength",      body.length());

        Document postDoc = postResp.parse();
        result.put("postTitle",       postDoc.title());

        // Element counts — tells us the structure
        Map<String,Integer> counts = new LinkedHashMap<>();
        counts.put("table",   postDoc.select("table").size());
        counts.put("tr",      postDoc.select("tr").size());
        counts.put("th",      postDoc.select("th").size());
        counts.put("td",      postDoc.select("td").size());
        counts.put("div",     postDoc.select("div").size());
        counts.put("script",  postDoc.select("script").size());
        counts.put("form",    postDoc.select("form").size());
        result.put("elementCounts", counts);

        // All table headers (to understand column names)
        result.put("allTableHeaders", postDoc.select("th").stream().map(Element::text).distinct().toList());

        // Script contents that might hold data
        List<String> scriptSnippets = postDoc.select("script:not([src])").stream()
                .map(s -> s.html().trim())
                .filter(s -> s.length() > 20)
                .map(s -> s.substring(0, Math.min(300, s.length())))
                .toList();
        result.put("inlineScripts", scriptSnippets);

        // First 500 chars of any div that looks like it holds schedule data
        List<String> dataDivs = postDoc.select("div").stream()
                .filter(d -> d.text().length() > 50)
                .map(d -> d.attr("class") + ": " + d.text().substring(0, Math.min(200, d.text().length())))
                .limit(10)
                .toList();
        result.put("contentDivs", dataDivs);

        // Raw body — first 4000 chars so you can see the actual HTML structure
        result.put("rawBodyFirst4000", body.substring(0, Math.min(4000, body.length())));

        return result;
    }

    @GetMapping("/api/debug") @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String,Object> debug() {
        List<TimetableLesson> lessons = scraperService.fetchAll();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("total",      lessons.size());
        m.put("ready",      scraperService.isCacheReady());
        m.put("scraping",   scraperService.isScraping());
        m.put("sample",     lessons.stream().limit(5).toList());
        return m;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "[]"; }
    }
}