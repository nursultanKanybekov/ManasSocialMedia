package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.web.dto.auth.MabisTeacherInfo;
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
import java.util.ArrayList;
import java.util.List;

/**
 * MabisAuthService
 *
 * Authenticates against the Manas MABIS portal (info.manas.edu.kg) and scrapes
 * verified teacher/staff data.
 *
 * Flow:
 *  1. GET  /index.php?r=site/login  → extract _csrf-frontend token
 *  2. POST /index.php?r=site/login  → submit credentials
 *  3. Parse POST response            → check success marker (site/logout)
 *  4. GET  /index.php?r=site/profile → scrape teacher name, employee number, department
 *
 * Key selectors (based on real MABIS HTML):
 *  - Employee number:  navbar span "01447 ▾" OR .profile-user-image alt / src path
 *  - Full name:        .user-header p first line, or <h4> in profile card
 *  - Avatar URL:       .profile-user-image[src]
 *  - Department/Faculty: table rows in profile detail view
 */
@Service
@Slf4j
public class MabisAuthService {

    private static final String BASE_URL    = "https://info.manas.edu.kg";
    private static final String LOGIN_URL   = BASE_URL + "/index.php?r=site%2Flogin";
    private static final String PROFILE_URL = BASE_URL + "/index.php?r=site%2Fprofile";

    /** Present on login page, absent after successful auth */
    private static final String LOGIN_INDICATOR = "Системага кирүү";
    /** Present only after successful login */
    private static final String SUCCESS_MARKER  = "site/logout";

    private static final int TIMEOUT_SECONDS = 15;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Authenticates and returns verified teacher data from MABIS.
     * Throws MabisAuthException if credentials wrong or portal unreachable.
     */
    public MabisTeacherInfo authenticateAndFetch(String username, String password) {
        log.info("MABIS: authenticating '{}'", username);

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
            log.debug("MABIS: CSRF token extracted");

            // Step 2: POST credentials
            String postResponse = postCredentials(client, username, password, csrf);

            // Step 3: Detect login failure
            boolean loginFailed = postResponse.contains(LOGIN_INDICATOR)
                    && !postResponse.contains(SUCCESS_MARKER);
            if (loginFailed) {
                log.warn("MABIS: login failed for '{}'", username);
                throw new MabisAuthException("Invalid MABIS username or password.");
            }
            log.info("MABIS: login successful for '{}'", username);

            // Step 4: Fetch profile page for full teacher data
            String profileHtml;
            if (postResponse.contains(SUCCESS_MARKER) && postResponse.length() > 5000) {
                log.debug("MABIS: using POST response as profile page");
                profileHtml = postResponse;
            } else {
                log.debug("MABIS: fetching profile page separately");
                profileHtml = fetchPage(client, PROFILE_URL);
            }

            MabisTeacherInfo info = scrapeProfile(profileHtml, username);
            log.info("MABIS: scraped — name='{}' employee='{}' dept='{}'",
                    info.getFullName(), info.getEmployeeNumber(), info.getDepartmentName());
            return info;

        } catch (MabisAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("MABIS: error for '{}'", username, e);
            throw new MabisAuthException(
                    "MABIS portal is unreachable. Please try again.", e);
        }
    }

    // ── Step 1: Extract CSRF ───────────────────────────────────────────────────

    private String fetchCsrfToken(CloseableHttpClient client) throws Exception {
        String html = fetchPage(client, LOGIN_URL);
        Document doc = Jsoup.parse(html);

        // MABIS uses _csrf-frontend in meta tag
        Element meta = doc.selectFirst("meta[name=csrf-token]");
        if (meta != null && !meta.attr("content").isBlank()) {
            return meta.attr("content");
        }
        // Also try hidden input variants
        for (String name : new String[]{"_csrf-frontend", "_csrf"}) {
            Element input = doc.selectFirst("input[name=" + name + "]");
            if (input != null && !input.val().isBlank()) {
                return input.val();
            }
        }
        throw new MabisAuthException("Could not extract CSRF token from MABIS portal.");
    }

    // ── Step 2: POST credentials ───────────────────────────────────────────────

    private String postCredentials(CloseableHttpClient client,
                                   String username, String password,
                                   String csrf) throws Exception {
        HttpPost post = new HttpPost(LOGIN_URL);
        setHeaders(post, LOGIN_URL);

        List<NameValuePair> params = new ArrayList<>();
        // MABIS uses _csrf-frontend as the param name in the form body
        params.add(new BasicNameValuePair("_csrf-frontend",         csrf));
        params.add(new BasicNameValuePair("LoginForm[username]",    username));
        params.add(new BasicNameValuePair("LoginForm[password]",    password));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = client.execute(post)) {
            log.debug("MABIS: POST status={}", response.getCode());
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    // ── Step 3: Scrape teacher profile ────────────────────────────────────────

    private MabisTeacherInfo scrapeProfile(String html, String mabisUsername) {
        Document doc = Jsoup.parse(html, BASE_URL);

        // ── Employee number ────────────────────────────────────────────────────
        // Navbar shows: "01447 ▾" in <span class="d-none d-md-inline">
        String employeeNumber = null;
        Element navSpan = doc.selectFirst("span.d-none.d-md-inline");
        if (navSpan != null) {
            String text = navSpan.text().trim();
            // Extract only the numeric part before any space/arrow
            String[] parts = text.split("\\s+");
            if (parts.length > 0 && parts[0].matches("\\d+")) {
                employeeNumber = parts[0];
            }
        }
        // Fallback: extract from avatar image path /uploads/profile/01447.jpg
        if (isBlank(employeeNumber)) {
            Element avatarImg = doc.selectFirst("img.profile-user-image");
            if (avatarImg != null) {
                String src = avatarImg.attr("src");
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("/uploads/profile/(\\d+)\\.jpg").matcher(src);
                if (m.find()) employeeNumber = m.group(1);
            }
        }
        if (isBlank(employeeNumber)) employeeNumber = mabisUsername;

        // ── Full name ──────────────────────────────────────────────────────────
        String fullName = null;

        // Source 1: .user-header <p> — first line before <br>
        Element userHeaderP = doc.selectFirst(".user-header p");
        if (userHeaderP != null) {
            // The header p usually starts with the name
            String text = userHeaderP.ownText().trim();
            if (!isBlank(text)) fullName = text;
        }

        // Source 2: <h4> anywhere in profile card area
        if (isBlank(fullName)) {
            Element h4 = doc.selectFirst(".profile-usertitle h4, h4.profile-name, h4");
            if (h4 != null && !isBlank(h4.text())) fullName = h4.text().trim();
        }

        // Source 3: navbar brand span (usually shows employee number, skip if numeric-only)
        if (isBlank(fullName) && navSpan != null) {
            String t = navSpan.text().trim();
            if (!t.matches("\\d+.*")) fullName = t;
        }

        if (isBlank(fullName)) fullName = mabisUsername;

        // ── Avatar URL ─────────────────────────────────────────────────────────
        String avatarUrl = null;
        Element avatarEl = doc.selectFirst("img.profile-user-image");
        if (avatarEl == null) avatarEl = doc.selectFirst(".user-image.img-circle");
        if (avatarEl != null) {
            String src = avatarEl.absUrl("src");
            if (!src.isBlank() && !src.contains("avatar_female") && !src.contains("default")) {
                avatarUrl = src;
            }
        }

        // ── Department / Faculty ───────────────────────────────────────────────
        String departmentName = null;

        // Try profile detail table rows (label → value pairs)
        for (Element row : doc.select("table tbody tr, .detail-view tbody tr")) {
            Element th = row.selectFirst("th, td:first-child");
            Element td = row.selectFirst("td:last-child");
            if (th == null || td == null) continue;
            String label = th.text().trim().toLowerCase();
            if (label.contains("факульт") || label.contains("бөлүм")
                    || label.contains("department") || label.contains("faculty")
                    || label.contains("кафедра") || label.contains("kafedra")
                    || label.contains("bolum") || label.contains("birim")) {
                departmentName = td.text().trim();
                if (!isBlank(departmentName)) break;
            }
        }

        // Fallback: look for any element containing department-like text
        if (isBlank(departmentName)) {
            for (Element el : doc.select("p, li, span, small")) {
                String t = el.text().trim();
                if (t.toLowerCase().contains("факультет") || t.toLowerCase().contains("бөлүм")
                        || t.toLowerCase().contains("кафедра")) {
                    int colon = t.indexOf(':');
                    departmentName = (colon >= 0 ? t.substring(colon + 1) : t).trim();
                    if (!isBlank(departmentName) && departmentName.length() < 150) break;
                }
            }
        }

        // Derive email from employee number
        String mabisEmail = employeeNumber + "@manas.edu.kg";

        log.debug("MABIS scrape — name='{}' employee='{}' dept='{}' avatar={}",
                fullName, employeeNumber, departmentName,
                avatarUrl != null ? "present" : "absent");

        return MabisTeacherInfo.builder()
                .fullName(fullName)
                .employeeNumber(employeeNumber)
                .mabisEmail(mabisEmail)
                .avatarUrl(avatarUrl)
                .departmentName(departmentName)
                .mabisUsername(mabisUsername)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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

    public static class MabisAuthException extends RuntimeException {
        public MabisAuthException(String message) { super(message); }
        public MabisAuthException(String message, Throwable cause) { super(message, cause); }
    }
}