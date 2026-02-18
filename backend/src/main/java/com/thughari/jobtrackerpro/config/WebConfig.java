package com.thughari.jobtrackerpro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Serve files from the "uploads" directory for the /api/storage/files/** endpoint in local environment
		String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

		registry.addResourceHandler("/api/storage/files/**")
		.addResourceLocations(uploadPath);
	}
}