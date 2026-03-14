package com.thughari.jobtrackerpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "dashboardExecutor")
	public AsyncTaskExecutor dashboardExecutor() {
		return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("DashVT-", 0).factory()));
	}

	@Primary
	@Bean(name = "taskExecutor")
	public AsyncTaskExecutor taskExecutor() {
		return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("GmailSyncVT-", 0).factory()));
	}
}