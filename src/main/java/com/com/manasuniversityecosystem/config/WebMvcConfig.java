package com.com.manasuniversityecosystem.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CurrentUriInterceptor currentUriInterceptor;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /** The public server URL — set APP_BASE_URL on the server */
    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentUriInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absoluteUploadPath);
    }

    /**
     * CORS: allow the Swagger UI (served from the same server) and common
     * local dev origins to call the /api/** endpoints.
     * On production set APP_BASE_URL=https://yourdomain.com so requests
     * from that origin are explicitly allowed.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        baseUrl,
                        "http://localhost:8081",
                        "http://localhost:3000",
                        "http://127.0.0.1:8081"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}