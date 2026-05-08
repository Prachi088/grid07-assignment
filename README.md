# Grid07 Backend Engineering Assignment
## Spring Boot Microservice with Redis Guardrails

## Tech Stack
- Java 17+, Spring Boot 3.3.5
- PostgreSQL (via Docker)
- Redis (via Docker)
- Maven

## How to Run

### 1. Start Docker containers
```bash
docker-compose up -d
```

### 2. Run the application
```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
```

### 3. App runs on
http://localhost:8080

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/posts | Create a new post |
| POST | /api/posts/{postId}/like | Like a post (+20 virality) |
| POST | /api/posts/{postId}/comments | Add a comment |

---

## Architecture

### PostgreSQL
Acts as source of truth for all persistent data:
- Users, Bots, Posts, Comments tables
- All content is stored here permanently

### Redis
Acts as the gatekeeper and real-time engine:
- Virality scores per post
- Bot reply counters
- Cooldown TTL keys
- Pending notification queues

---

## Phase 2: Thread Safety for Atomic Locks

### The Problem
When 200 concurrent bot requests hit the API simultaneously,
a naive check like "if count < 100 then save" would fail
because all 200 threads could read count=99 at the same time
and all 200 would pass the check → 200 comments saved.

### The Solution: Redis INCR
Redis INCR is a single atomic operation. It increments and
returns the new value in one step — no race condition possible.

```java
Long botCount = redisTemplate.opsForValue().increment(botCountKey);
if (botCount > 100) {
    redisTemplate.opsForValue().decrement(botCountKey);
    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ...);
}
```

### How it works:
- Thread 1 calls INCR → gets 100 → allowed
- Thread 101 calls INCR → gets 101 → rejected + decremented back
- Redis processes these sequentially at the atomic level
- Database only gets written if Redis allows it
- Result: Exactly 100 bot comments, never 101

### Three Guardrails:
1. **Horizontal Cap** - Max 100 bot replies per post (INCR)
2. **Vertical Cap** - Max depth 20 per thread (simple check)
3. **Cooldown Cap** - Bot cooldown per human per 10 mins (TTL key)

---

## Phase 3: Notification Engine

- Bot interaction → check 15 min cooldown key
- If on cooldown → push to Redis List (queue)
- If not → log immediately + set 15 min TTL
- CRON job runs every 5 mins → pops all pending → logs summary

---

## Project Structure
src/main/java/com/grid07/assignment/
├── entity/          → JPA entities (User, Bot, Post, Comment)
├── repository/      → Spring Data JPA repositories
├── service/         → Business logic + Redis operations
├── controller/      → REST endpoints
└── scheduler/       → CRON notification sweeper

