package com.thughari.jobtrackerpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // High Performance: BCrypt is standard, but you can tune strength 
        // if login latency becomes a bottleneck. 10 is the balanced default.
        return new BCryptPasswordEncoder();
    }
}
