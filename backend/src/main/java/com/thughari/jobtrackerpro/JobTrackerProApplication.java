package com.thughari.jobtrackerpro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class JobTrackerProApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobTrackerProApplication.class, args);
	}

}
