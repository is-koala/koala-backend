package com.exskylab.koala.core.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves committed QA visuals without exposing the source folder name. */
@Configuration
public class QaAssetConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/qa-assets/**")
                .addResourceLocations("classpath:/static/demo-assets/");
    }
}
