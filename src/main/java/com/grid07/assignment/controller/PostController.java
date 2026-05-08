package com.grid07.assignment.controller;

import com.grid07.assignment.entity.Comment;
import com.grid07.assignment.entity.Post;
import com.grid07.assignment.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ─── Create Post ───────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Post post) {
        Post saved = postService.createPost(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ─── Add Comment ───────────────────────────────────────────
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable Long postId,
            @RequestBody Comment comment) {
        Comment saved = postService.addComment(postId, comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ─── Like Post ─────────────────────────────────────────────
    @PostMapping("/{postId}/like")
    public ResponseEntity<String> likePost(@PathVariable Long postId) {
        postService.likePost(postId);
        return ResponseEntity.ok("Post liked! Virality +20");
    }
}