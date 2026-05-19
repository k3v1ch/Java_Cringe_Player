package com.cringe.volume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    @Value("${public.web-url:http://localhost:8080}")
    private String webUrl;

    @Value("${public.pay-url:http://localhost:8080}")
    private String payUrl;

    @Value("${public.backend-url:http://localhost:8080}")
    private String backendUrl;

    /**
     * Дополнительные origin-ы через CORS_EXTRA_ORIGINS=https://a.example,https://b.example
     */
    @Value("${cors.extra-origins:}")
    private String extraOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                Set<String> origins = new LinkedHashSet<>();
                origins.add(webUrl);
                origins.add(payUrl);
                origins.add(backendUrl);
                origins.add("http://localhost:8080");
                origins.add("http://localhost:3000");
                origins.add("http://127.0.0.1:8080");

                if (extraOrigins != null && !extraOrigins.isBlank()) {
                    for (String o : extraOrigins.split(",")) {
                        String trimmed = o.trim();
                        if (!trimmed.isEmpty()) origins.add(trimmed);
                    }
                }

                List<String> originList = new ArrayList<>(origins);

                registry.addMapping("/api/**")
                        .allowedOrigins(originList.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                        .allowedHeaders("*")
                        .exposedHeaders("Content-Range", "Accept-Ranges", "Content-Length")
                        .allowCredentials(false);
            }
        };
    }
}
