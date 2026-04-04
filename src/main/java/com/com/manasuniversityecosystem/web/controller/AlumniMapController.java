package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/mezun/map")
@RequiredArgsConstructor
@Slf4j
public class AlumniMapController {

    private final ProfileRepository profileRepo;
    private final UserRepository    userRepo;

    // ─────────────────────────────────────────────────────────────
    // 1.  MAP PAGE — visible to ALL authenticated users
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String mapPage(@AuthenticationPrincipal UserDetailsImpl principal,
                          Model model) {
        boolean isMezun = principal.getRole() == UserRole.MEZUN;
        model.addAttribute("isMezun", isMezun);

        // For MEZUN: show their current pin state so the UI can pre-fill
        if (isMezun) {
            profileRepo.findByUserId(principal.getId()).ifPresent(p -> {
                model.addAttribute("myLat",       p.getMapLat());
                model.addAttribute("myLng",       p.getMapLng());
                model.addAttribute("myCity",      p.getMapCity());
                model.addAttribute("myCountry",   p.getMapCountry());
                model.addAttribute("myShowOnMap", p.getShowOnMap());
            });
        }
        return "mezun/map";
    }

    // ─────────────────────────────────────────────────────────────
    // 2.  MAP DATA API — JSON array of all pinned alumni
    //     Called by Leaflet on page load (and can be polled)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/pins")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getPins() {
        List<Profile> pins = profileRepo.findAllMapPins();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Profile p : pins) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("lat",            p.getMapLat());
            dto.put("lng",            p.getMapLng());
            dto.put("city",           nvl(p.getMapCity()));
            dto.put("country",        nvl(p.getMapCountry()));
            dto.put("name",           p.getUser().getFullName());
            dto.put("avatarUrl",      nvl(p.getAvatarUrl()));
            dto.put("faculty",        p.getUser().getFaculty() != null
                    ? p.getUser().getFaculty().getName() : "");
            dto.put("graduationYear", p.getUser().getGraduationYear());
            dto.put("jobTitle",       nvl(p.getCurrentJobTitle()));
            dto.put("company",        nvl(p.getCurrentCompany()));
            dto.put("headline",       nvl(p.getHeadline()));
            dto.put("profileId",      p.getUser().getId().toString());
            result.add(dto);
        }
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────
    // 3.  SET PIN — MEZUN only
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/pin")
    @PreAuthorize("hasRole('MEZUN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setPin(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody Map<String, Object> body) {
        try {
            Double lat     = toDouble(body.get("lat"));
            Double lng     = toDouble(body.get("lng"));
            String city    = str(body.get("city"));
            String country = str(body.get("country"));

            if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid coordinates."));

            Profile profile = profileRepo.findByUserId(principal.getId())
                    .orElseThrow(() -> new IllegalStateException("Profile not found"));

            profile.setMapLat(lat);
            profile.setMapLng(lng);
            profile.setMapCity(city.isBlank() ? null : city);
            profile.setMapCountry(country.isBlank() ? null : country);
            profile.setShowOnMap(true);
            profileRepo.save(profile);

            log.info("Alumni {} pinned location: {}, {} ({}, {})",
                    principal.getUsername(), lat, lng, city, country);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "message", "Your pin has been placed on the map!"
            ));
        } catch (Exception e) {
            log.error("Set map pin error", e);
            return ResponseEntity.ok(Map.of("error", "Could not save pin: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4.  REMOVE PIN — MEZUN only (opt-out)
    // ─────────────────────────────────────────────────────────────

    @DeleteMapping("/pin")
    @PreAuthorize("hasRole('MEZUN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePin(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            Profile profile = profileRepo.findByUserId(principal.getId())
                    .orElseThrow(() -> new IllegalStateException("Profile not found"));

            profile.setShowOnMap(false);
            profileRepo.save(profile);

            return ResponseEntity.ok(Map.of("ok", true, "message", "Pin removed."));
        } catch (Exception e) {
            log.error("Remove map pin error", e);
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static String nvl(String s) { return s != null ? s : ""; }

    private static String str(Object o) {
        return o instanceof String s ? s.trim() : "";
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}