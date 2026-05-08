package com.grid07.assignment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final StringRedisTemplate redisTemplate;

    // ─── Called when a bot interacts with a user's post ────────
    public void handleBotNotification(Long postId, Long userId) {

        String cooldownKey = "notif_cooldown:user_" + userId;
        String pendingKey  = "user:" + userId + ":pending_notifs";

        Boolean onCooldown = redisTemplate.hasKey(cooldownKey);

        if (Boolean.TRUE.equals(onCooldown)) {
            // User already got a notif recently → push to pending queue
            String message = "A bot replied to your post " + postId;
            redisTemplate.opsForList().rightPush(pendingKey, message);
            log.info("Notification queued for user {}: {}", userId, message);

        } else {
            // No cooldown → send immediately and set 15 min cooldown
            log.info("Push Notification Sent to User {}: bot replied to post {}", userId, postId);
            redisTemplate.opsForValue().set(cooldownKey, "1", 15, TimeUnit.MINUTES);
        }
    }
}