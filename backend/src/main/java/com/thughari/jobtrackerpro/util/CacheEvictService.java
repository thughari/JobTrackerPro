package com.thughari.jobtrackerpro.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Slf4j
public class CacheEvictService {

    private final CacheManager cacheManager;
    private static final List<String> USER_BUCKETS = List.of("users", "userEntities");
    private static final List<String> DATA_BUCKETS = List.of("jobList", "jobDashboard");

    public CacheEvictService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    public void evictAllForUser(String email) {
        if (email == null) return;
        final String key = email.toLowerCase().trim();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    performEviction(key);
                }
            });
        } else {
            performEviction(key);
        }
    }

    private void performEviction(String key) {
        USER_BUCKETS.forEach(bucket -> evict(bucket, key));
        DATA_BUCKETS.forEach(bucket -> evict(bucket, key));
        
        Cache pages = cacheManager.getCache("jobPages");
        if (pages != null) pages.clear();
    }

    private void evict(String name, String key) {
        Cache c = cacheManager.getCache(name);
        if (c != null) c.evict(key);
    }
}