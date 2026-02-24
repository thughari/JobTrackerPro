package com.thughari.jobtrackerpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "dashboardExecutor")
	public Executor dashboardExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4); 
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("DashThread-");
		executor.initialize();
		return executor;
	}
	
	@Primary // This tells Spring: "Use this one for @Async by default"
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("GmailSync-");
        executor.initialize();
        return executor;
    }
}