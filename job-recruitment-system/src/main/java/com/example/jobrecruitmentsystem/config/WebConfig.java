package com.example.jobrecruitmentsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // This part was already here
    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * This is the new method you need to add.
     * It tells Spring that any web request for "/images/..."
     * should look for a file inside your "resources/images/" folder.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/images/");
    }
}