package com.grid07.assignment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class NotificationScheduler {

    private final StringRedisTemplate redisTemplate;

    // Runs every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void sweepPendingNotifications() {

        log.info("CRON Sweeper running...");

        // Find all pending notif keys in Redis
        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");

        if (keys == null || keys.isEmpty()) {
            log.info("No pending notifications found.");
            return;
        }

        for (String key : keys) {

            // Pop all messages from the Redis List
            List<String> notifications = redisTemplate.opsForList().range(key, 0, -1);

            if (notifications == null || notifications.isEmpty()) continue;

            int count = notifications.size();
            String first = notifications.get(0);

            // Log summarized message
            if (count == 1) {
                log.info("Summarized Push Notification: {}", first);
            } else {
                log.info("Summarized Push Notification: {} and [{}] others interacted with your posts.",
                        first, count - 1);
            }

            // Clear the Redis list for this user
            redisTemplate.delete(key);
        }
    }
}