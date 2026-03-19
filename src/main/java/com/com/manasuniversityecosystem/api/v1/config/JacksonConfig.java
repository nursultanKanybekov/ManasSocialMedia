package com.com.manasuniversityecosystem.api.v1.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class JacksonConfig {

    @Bean("snakeCaseMapper")
    public ObjectMapper snakeCaseMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public WebMvcConfigurer apiJacksonConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                // Update the existing MappingJackson2HttpMessageConverter in place
                // instead of removing and re-adding (which breaks SpringDoc)
                converters.stream()
                        .filter(c -> c instanceof MappingJackson2HttpMessageConverter)
                        .map(c -> (MappingJackson2HttpMessageConverter) c)
                        .forEach(c -> c.setObjectMapper(snakeCaseMapper()));
            }
        };
    }
}