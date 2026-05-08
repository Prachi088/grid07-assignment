package com.grid07.assignment.service;

import com.grid07.assignment.entity.Comment;
import com.grid07.assignment.entity.Post;
import com.grid07.assignment.repository.CommentRepository;
import com.grid07.assignment.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;

    // ─── Create Post ───────────────────────────────────────────
    public Post createPost(Post post) {
        return postRepository.save(post);
    }

    // ─── Like Post ─────────────────────────────────────────────
    public void likePost(Long postId) {
        // Human like = +20 virality points
        redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 20);
        log.info("Post {} liked. Virality +20", postId);
    }

    // ─── Add Comment ───────────────────────────────────────────
    public Comment addComment(Long postId, Comment comment) {

        boolean isBot = comment.getAuthorId() < 0;
        // Convention: negative authorId = Bot, positive = Human
        // Bot authorId stored as negative e.g. -1 means botId=1

        if (isBot) {
            Long botId = Math.abs(comment.getAuthorId());
            runBotGuardrails(postId, botId, comment.getDepthLevel());
        }

        comment.setPostId(postId);
        Comment saved = commentRepository.save(comment);

        // Update virality score
        if (isBot) {
            redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 1);
        } else {
            redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 50);
            // Trigger notification engine for human post owner
            notificationService.handleBotNotification(postId, comment.getAuthorId());
        }

        return saved;
    }

    // ─── Redis Guardrails ──────────────────────────────────────
    private void runBotGuardrails(Long postId, Long botId, int depthLevel) {

        // 1. Vertical Cap: depth > 20 → reject
        if (depthLevel > 20) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vertical cap exceeded: max depth is 20");
        }

        // 2. Horizontal Cap: bot_count >= 100 → reject
        String botCountKey = "post:" + postId + ":bot_count";
        Long botCount = redisTemplate.opsForValue().increment(botCountKey);
        if (botCount > 100) {
            // Decrement back since we're rejecting
            redisTemplate.opsForValue().decrement(botCountKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Horizontal cap exceeded: max 100 bot replies per post");
        }

        // 3. Cooldown Cap: bot cannot interact with same human within 10 mins
        String cooldownKey = "cooldown:bot_" + botId + ":post_" + postId;
        Boolean exists = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Cooldown active: bot " + botId + " must wait 10 minutes");
        }
        // Set cooldown key with 10 min TTL
        redisTemplate.opsForValue().set(cooldownKey, "1", 10, TimeUnit.MINUTES);
    }
}