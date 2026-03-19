package com.com.manasuniversityecosystem.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice("webGlobalExceptionHandler")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNoResource(NoResourceFoundException ex) {
        // Chrome DevTools and browsers probe well-known paths — silently ignore
        log.debug("Static resource not found: {}", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException ex, Model model) {
        log.warn("Not found: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("statusCode", 404);
        return "error/404";
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(SecurityException ex, Model model) {
        log.warn("Forbidden: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("statusCode", 403);
        return "error/403";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("errorMessage", "You do not have permission to access this resource.");
        model.addAttribute("statusCode", 403);
        return "error/403";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalStateException ex, Model model) {
        log.warn("Bad request: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("statusCode", 400);
        return "error/400";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleGeneric(Exception ex,
                                jakarta.servlet.http.HttpServletRequest request,
                                Model model) {
        log.error("Unexpected error on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        String htmxHeader = request.getHeader("HX-Request");
        if ("true".equals(htmxHeader)) {
            return org.springframework.http.ResponseEntity
                    .status(500)
                    .body("<div class='alert alert-error' style='margin:1rem'>" +
                            "Something went wrong loading this section. " +
                            "Error: " + ex.getMessage() + "</div>");
        }
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
        model.addAttribute("statusCode", 500);
        return "error/500";
    }
}