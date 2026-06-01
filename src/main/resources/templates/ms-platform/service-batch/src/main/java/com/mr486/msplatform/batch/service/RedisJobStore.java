package com.mr486.msplatform.batch.service;

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

/**
 * Magasin de jobs batch persistent dans Redis : sérialise/désérialise les
 * {@link BatchJobResponse} en JSON avec un TTL de 24 h, et maintient des sets
 * d'index ({@code BATCH_JOBS_ALL}, {@code batchUserJobs}) pour les requêtes par utilisateur.
 */
@Service
@RequiredArgsConstructor
public class RedisJobStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Persiste un job dans Redis et met à jour les index globaux et par utilisateur.
     *
     * @param job le job à sauvegarder
     */
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

    /**
     * Recherche un job par son identifiant.
     *
     * @param id l'identifiant unique du job
     * @return le job trouvé, ou {@code null} s'il n'existe pas ou a expiré
     */
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

    /**
     * Retourne tous les jobs appartenant à un utilisateur donné.
     *
     * @param userId l'identifiant de l'utilisateur
     * @return la liste des jobs de l'utilisateur (vide si aucun)
     */
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

    /**
     * Calcule des statistiques agrégées sur l'ensemble des jobs (par statut + total).
     *
     * @return une map statut → nombre de jobs ; inclut la clé {@code total}
     */
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
