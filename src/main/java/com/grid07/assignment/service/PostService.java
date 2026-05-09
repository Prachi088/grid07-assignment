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

    public Post createPost(Post post) {
        return postRepository.save(post);
    }

    public void likePost(Long postId) {
        redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 20);
        log.info("Post {} liked. Virality +20", postId);
    }

    public Comment addComment(Long postId, Comment comment) {

        boolean isBot = comment.getAuthorId() < 0;

        if (isBot) {
            Long botId = Math.abs(comment.getAuthorId());
            runBotGuardrails(postId, botId, comment.getDepthLevel());
        }

        comment.setPostId(postId);
        Comment saved = commentRepository.save(comment);

        if (isBot) {
            redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 1);
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
            notificationService.handleBotNotification(postId, post.getAuthorId());
        } else {
            redisTemplate.opsForValue().increment("post:" + postId + ":virality_score", 50);
        }

        return saved;
    }

    private void runBotGuardrails(Long postId, Long botId, int depthLevel) {

        if (depthLevel > 20) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vertical cap exceeded: max depth is 20");
        }

        String botCountKey = "post:" + postId + ":bot_count";
        Long botCount = redisTemplate.opsForValue().increment(botCountKey);
        if (botCount > 100) {
            redisTemplate.opsForValue().decrement(botCountKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Horizontal cap exceeded: max 100 bot replies per post");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        Long humanId = post.getAuthorId();

        String cooldownKey = "cooldown:bot_" + botId + ":human_" + humanId;
        Boolean exists = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForValue().decrement(botCountKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Cooldown active: bot " + botId + " must wait 10 minutes");
        }
        redisTemplate.opsForValue().set(cooldownKey, "1", 10, TimeUnit.MINUTES);
    }
}