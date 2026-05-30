package com.mr486.msplatform.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.common.batch.BatchJobResponse;
import com.mr486.msplatform.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisJobStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(BatchJobResponse job) {
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.batchJob(job.getJobId()),
                    objectMapper.writeValueAsString(job),
                    TTL
            );
            redisTemplate.opsForSet().add(
                    RedisKeys.BATCH_JOBS_ALL,
                    job.getJobId()
            );
            redisTemplate.opsForSet().add(
                    RedisKeys.batchUserJobs(job.getUserId()),
                    job.getJobId()
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public BatchJobResponse find(String id) {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.batchJob(id));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, BatchJobResponse.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<BatchJobResponse> findByUser(Long userId) {
        Set<String> ids = redisTemplate.opsForSet().members(RedisKeys.batchUserJobs(userId));
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .map(this::find)
                .filter(Objects::nonNull)
                .toList();
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String key : List.of("pending", "processing", "retrying", "completed", "failed", "dead")) {
            stats.put(key, 0L);
        }

        Set<String> ids = redisTemplate.opsForSet().members(RedisKeys.BATCH_JOBS_ALL);
        long total = 0;

        if (ids != null) {
            for (String id : ids) {
                BatchJobResponse job = find(id);
                if (job != null && job.getStatus() != null) {
                    stats.merge(job.getStatus().name().toLowerCase(), 1L, Long::sum);
                    total++;
                }
            }
        }

        stats.put("total", total);
        return stats;
    }
}
