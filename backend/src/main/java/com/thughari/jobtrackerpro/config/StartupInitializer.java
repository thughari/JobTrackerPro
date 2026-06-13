package com.thughari.jobtrackerpro.config;

import com.thughari.jobtrackerpro.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    public StartupInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("System Startup: Resetting all active Gmail sync locks.");
        userRepository.resetAllSyncLocks();
    }
}
