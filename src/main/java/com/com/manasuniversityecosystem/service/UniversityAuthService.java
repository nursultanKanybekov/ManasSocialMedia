package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UniversityAuthService
 *
 * Authenticates against Manas OBIS portal and scrapes verified student data.
 *
 * Flow:
 *  1. GET  /site/login    → extract _csrf token + PHPSESSID
 *  2. POST /site/login    → submit credentials
 *  3. Parse POST response → already contains full dashboard with student data
 *     (OBIS redirects to /s-main/view after login, HttpClient follows it)
 *  4. If session needed:  GET /s-main/view explicitly for clean profile page
 *
 * Key selectors (verified from real OBIS HTML):
 *  - Full name:      h4 inside .d-flex.flex-column.align-items-center
 *  - Student ID:     table#w0 first <td>
 *  - First name:     table#w0 row where <th> = "Аты"
 *  - Last name:      table#w0 row where <th> = "Фамилиясы"
 *  - Avatar URL:     img.myImg[src]
 *  - Admission year: <small> text containing "Кабыл алынган жылы:"
 *  - Study year:     calculated from admission year + current academic year
 *  - Email:          .info_text text after ":"
 */
@Service
@Slf4j
public class UniversityAuthService {

    private static final String BASE_URL        = "https://obistest.manas.edu.kg";
    private static final String LOGIN_URL       = BASE_URL + "/site/login";
    private static final String PROFILE_URL     = BASE_URL + "/s-main/view";

    // Login page indicator — present ONLY on login page, absent after successful auth
    private static final String LOGIN_INDICATOR = "Системага кирүү";
    // Secondary success check — only present after login
    private static final String SUCCESS_MARKER  = "site/logout";

    private static final int TIMEOUT_SECONDS = 15;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Authenticates and returns verified student data from OBIS.
     * Throws UniversityAuthException if credentials wrong or portal unreachable.
     */
    public ObisStudentInfo authenticateAndFetch(String username, String password) {
        log.info("OBIS: authenticating '{}'", username);

        BasicCookieStore cookieStore = new BasicCookieStore();
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(TIMEOUT_SECONDS))
                .setResponseTimeout(Timeout.ofSeconds(TIMEOUT_SECONDS))
                .setRedirectsEnabled(true)
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(config)
                .build()) {

            // Step 1: GET login page → extract CSRF token
            String csrf = fetchCsrfToken(client);
            log.debug("OBIS: CSRF token extracted");

            // Step 2: POST credentials
            String postResponse = postCredentials(client, username, password, csrf);

            // Step 3: Detect login failure
            boolean loginFailed = postResponse.contains(LOGIN_INDICATOR)
                    && !postResponse.contains(SUCCESS_MARKER);
            if (loginFailed) {
                log.warn("OBIS: login failed for '{}'", username);
                throw new UniversityAuthException("Invalid OBIS username or password.");
            }
            log.info("OBIS: login successful for '{}'", username);

            // Step 4: If POST response already has profile data, parse it directly.
            // Otherwise fetch /s-main/view explicitly.
            String profileHtml;
            if (postResponse.contains("table") && postResponse.contains(SUCCESS_MARKER)) {
                log.debug("OBIS: using POST response as profile page");
                profileHtml = postResponse;
            } else {
                log.debug("OBIS: fetching profile page separately");
                profileHtml = fetchPage(client, PROFILE_URL);
            }

            ObisStudentInfo info = scrapeProfile(profileHtml, username);
            log.info("OBIS: scraped — name='{}' id='{}' year={} admYear={}",
                    info.getFullName(), info.getStudentId(),
                    info.getStudyYear(), info.getAdmissionYear());
            return info;

        } catch (UniversityAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OBIS: error for '{}'", username, e);
            throw new UniversityAuthException(
                    "University portal is unreachable. Please try again.", e);
        }
    }

    /** Simple boolean check for badge verification flow */
    public boolean authenticate(String username, String password) {
        try {
            authenticateAndFetch(username, password);
            return true;
        } catch (UniversityAuthException e) {
            if (e.getMessage().contains("unreachable")) throw e;
            return false;
        }
    }

    // ── Step 1: Extract CSRF ───────────────────────────────────────────────────

    private String fetchCsrfToken(CloseableHttpClient client) throws Exception {
        String html = fetchPage(client, LOGIN_URL);
        Document doc = Jsoup.parse(html);

        // Try hidden input first
        Element input = doc.selectFirst("input[name=_csrf]");
        if (input != null && !input.val().isBlank()) {
            return input.val();
        }
        // Try meta tag (Yii2 alternative location)
        Element meta = doc.selectFirst("meta[name=csrf-token]");
        if (meta != null && !meta.attr("content").isBlank()) {
            return meta.attr("content");
        }
        throw new UniversityAuthException("Could not extract CSRF token from OBIS portal.");
    }

    // ── Step 2: POST credentials ───────────────────────────────────────────────

    private String postCredentials(CloseableHttpClient client,
                                   String username, String password,
                                   String csrf) throws Exception {
        HttpPost post = new HttpPost(LOGIN_URL);
        setHeaders(post, LOGIN_URL);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("_csrf",                    csrf));
        params.add(new BasicNameValuePair("LoginForm[username]",      username));
        params.add(new BasicNameValuePair("LoginForm[password_hash]", password));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = client.execute(post)) {
            log.debug("OBIS: POST status={}", response.getCode());
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    // ── Step 3: Scrape student profile ────────────────────────────────────────

    /**
     * Parses the OBIS profile page using exact selectors verified from real HTML.
     *
     * Data sources:
     *  - Name:          <h4> in .d-flex.flex-column.align-items-center.text-center
     *                   OR navbar <span class="d-none d-md-inline">
     *  - Student ID:    First <td> in table#w0, OR student number row in detail table
     *  - First/Last:    Rows "Аты" / "Фамилиясы" in table#w0
     *  - Avatar:        img.myImg[src]  OR  .user-image[src]
     *  - Admission yr:  <small> containing "Кабыл алынган жылы:"
     *  - Study year:    Calculated from admission year
     *  - Email:         .info_text text
     */
    private ObisStudentInfo scrapeProfile(String html, String obisUsername) {
        Document doc = Jsoup.parse(html, BASE_URL);

        // ── Full name ──────────────────────────────────────────────────────────
        String fullName = null;

        // Source 1: <h4> in profile card
        Element h4 = doc.selectFirst(".d-flex.flex-column.align-items-center h4");
        if (h4 != null) fullName = h4.text().trim();

        // Source 2: navbar username span
        if (isBlank(fullName)) {
            Element navSpan = doc.selectFirst("span.d-none.d-md-inline");
            if (navSpan != null) fullName = navSpan.text().trim();
        }

        // Source 3: build from first + last name fields in the detail table
        if (isBlank(fullName)) {
            String firstName = getTableRowValue(doc, "Аты");
            String lastName  = getTableRowValue(doc, "Фамилиясы");
            String patronym  = getTableRowValue(doc, "Атасынын аты");
            if (!isBlank(lastName) && !isBlank(firstName)) {
                fullName = lastName.trim() + " " + firstName.trim()
                        + (isBlank(patronym) ? "" : " " + patronym.trim());
            }
        }

        // ── Student ID ─────────────────────────────────────────────────────────
        String studentId = getTableRowValue(doc, "Студенттик номер");
        if (isBlank(studentId)) {
            // Fallback: first <td> in table#w0
            Element firstTd = doc.selectFirst("table#w0 tbody tr td");
            if (firstTd != null) studentId = firstTd.text().trim();
        }
        if (isBlank(studentId)) studentId = obisUsername;

        // ── Avatar URL ─────────────────────────────────────────────────────────
        String avatarUrl = null;
        Element avatarImg = doc.selectFirst("img.myImg");
        if (avatarImg == null) avatarImg = doc.selectFirst("img.user-image.rounded-circle");
        if (avatarImg != null) {
            String src = avatarImg.absUrl("src");
            if (!src.isBlank()) avatarUrl = src;
        }

        // ── Admission year → Study year ───────────────────────────────────────
        Integer admissionYear = null;
        // Look for "Кабыл алынган жылы: 2023" in <small> tags or anywhere
        for (Element small : doc.select("small, p, span, li")) {
            String text = small.text();
            if (text.contains("Кабыл алынган жылы")) {
                Matcher m = Pattern.compile("(\\d{4})").matcher(text);
                if (m.find()) {
                    admissionYear = Integer.parseInt(m.group(1));
                    break;
                }
            }
        }
        // Also try the user-header <p> block: "Аяна Турсуналиева 2335.09016 Кабыл алынган жылы: 2023"
        if (admissionYear == null) {
            Element userHeader = doc.selectFirst(".user-header p");
            if (userHeader != null) {
                Matcher m = Pattern.compile("(\\d{4})").matcher(userHeader.text());
                while (m.find()) {
                    int yr = Integer.parseInt(m.group(1));
                    if (yr >= 2015 && yr <= LocalDate.now().getYear()) {
                        admissionYear = yr;
                        break;
                    }
                }
            }
        }

        Integer studyYear = calculateStudyYear(admissionYear);

        // ── Email ──────────────────────────────────────────────────────────────
        String obisEmail = null;
        Element infoText = doc.selectFirst(".info_text");
        if (infoText != null) {
            String text = infoText.text();
            int colonIdx = text.lastIndexOf(":");
            if (colonIdx >= 0) obisEmail = text.substring(colonIdx + 1).trim();
        }
        // Fallback: derive from student ID
        if (isBlank(obisEmail) && !isBlank(studentId)) {
            obisEmail = studentId + "@manas.edu.kg";
        }

        // ── Faculty name ───────────────────────────────────────────────────────
        // OBIS shows faculty in various places — try several selectors
        String facultyName = null;

        // Source 1: detail table row "Факультети" or "Факультет"
        for (String label : new String[]{"Факультети", "Факультет", "Факультеті"}) {
            facultyName = getTableRowValue(doc, label);
            if (!isBlank(facultyName)) break;
        }
        // Source 2: .user-header small or breadcrumb spans
        if (isBlank(facultyName)) {
            for (Element el : doc.select(".user-header small, .breadcrumb-item, .info-box-text")) {
                String t = el.text().trim();
                if (t.length() > 4 && !t.matches(".*\\d{4}.*") && t.contains(" ")) {
                    facultyName = t;
                    break;
                }
            }
        }
        // Source 3: any <td> or <li> containing "факультет" (case-insensitive)
        if (isBlank(facultyName)) {
            for (Element el : doc.select("td, li, p")) {
                String t = el.text().trim();
                if (t.toLowerCase().contains("факультет") && t.length() < 150) {
                    // Strip label prefix if present: "Факультети: Engineering" → "Engineering"
                    int colon = t.indexOf(':');
                    facultyName = (colon >= 0 ? t.substring(colon + 1) : t).trim();
                    if (!isBlank(facultyName)) break;
                }
            }
        }

        log.debug("OBIS scrape result — name='{}' id='{}' admYear={} studyYear={} faculty='{}' avatar={}",
                fullName, studentId, admissionYear, studyYear, facultyName,
                avatarUrl != null ? "present" : "absent");

        return ObisStudentInfo.builder()
                .fullName(isBlank(fullName) ? obisUsername : fullName)
                .studentId(studentId)
                .obisEmail(obisEmail)
                .avatarUrl(avatarUrl)
                .admissionYear(admissionYear)
                .studyYear(studyYear)
                .facultyName(facultyName)
                .obisUsername(obisUsername)
                .build();
    }

    /**
     * Calculates which year of study the student is in based on admission year.
     *
     * Academic year starts in September:
     *  - Admitted 2023, current date Sep 2024 → 2nd year
     *  - Admitted 2023, current date Mar 2025 → 2nd year (same academic year)
     *  - Admitted 2023, current date Sep 2025 → 3rd year
     *
     * This CANNOT be faked — it's derived from the admission year in OBIS.
     */
    private Integer calculateStudyYear(Integer admissionYear) {
        if (admissionYear == null) return null;

        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        // Academic year: Sep–Aug
        // If before September, we're still in the previous academic year
        int academicYearStart = (currentMonth >= 9) ? currentYear : currentYear - 1;

        int studyYear = academicYearStart - admissionYear + 1;

        // Clamp to valid range 1–6
        if (studyYear < 1) studyYear = 1;
        if (studyYear > 6) studyYear = 6;

        return studyYear;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Finds a value in the OBIS detail table by row header text.
     * Table structure: <tr><th>Аты</th><td>Аяна</td></tr>
     */
    private String getTableRowValue(Document doc, String headerText) {
        Elements rows = doc.select("table#w0 tbody tr, table.detail-view tbody tr");
        for (Element row : rows) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th != null && td != null && th.text().trim().equals(headerText)) {
                return td.text().trim();
            }
        }
        return null;
    }

    private String fetchPage(CloseableHttpClient client, String url) throws Exception {
        HttpGet get = new HttpGet(url);
        setHeaders(get, url);
        try (CloseableHttpResponse response = client.execute(get)) {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private void setHeaders(org.apache.hc.core5.http.HttpRequest request, String referer) {
        request.setHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        request.setHeader("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.setHeader("Accept-Language", "ru-RU,ru;q=0.9,ky;q=0.8,en;q=0.7");
        if (referer != null) request.setHeader("Referer", referer);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ── Domain exception ───────────────────────────────────────────────────────

    public static class UniversityAuthException extends RuntimeException {
        public UniversityAuthException(String message) { super(message); }
        public UniversityAuthException(String message, Throwable cause) { super(message, cause); }
    }
}