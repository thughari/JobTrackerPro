package com.thughari.jobtrackerpro;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class JobTrackerProApplication {

	public static void main(String[] args) {
		
		System.setProperty("user.timezone", "UTC");
	    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
		SpringApplication.run(JobTrackerProApplication.class, args);
	}

}
