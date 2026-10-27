package com.mathweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileServingConfig implements WebMvcConfigurer {

    @Value("${file.upload.images-path}")
    private String imagesPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imagesDir = Paths.get(imagesPath).toAbsolutePath().normalize();
        String location = "file:///" + imagesDir.toString().replace("\\", "/") + "/";

        // Images and thumbnails only. Videos are never served as static files.
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}