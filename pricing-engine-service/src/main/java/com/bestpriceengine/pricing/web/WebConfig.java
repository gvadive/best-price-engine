package com.bestpriceengine.pricing.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Local dev only: lets the static frontend (served on a different port)
        // call this API directly before nginx puts them on one origin (Task 7).
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET");
    }
}
