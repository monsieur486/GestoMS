package com.mr486.msplatform.consumer.controller;

import com.mr486.msplatform.common.batch.BatchJobMessage;
import com.mr486.msplatform.common.batch.BatchJobResponse;
import com.mr486.msplatform.common.batch.BatchJobStatus;
import com.mr486.msplatform.common.constants.RabbitQueues;
import com.mr486.msplatform.consumer.service.RedisJobStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur REST de gestion des travaux batch : soumet un job en file RabbitMQ,
 * interroge son état dans Redis et expose des statistiques agrégées.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BatchJobController {

    private final RedisJobStore store;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Crée un nouveau job batch pour l'utilisateur et l'envoie dans la queue {@code BATCH_JOBS}.
     * Génère 15 références de la forme {@code USER-<userId>-FAC-<NNN>}.
     *
     * @param userId      l'identifiant de l'utilisateur propriétaire du job
     * @param failureRate le pourcentage de chance de simuler un échec (0 = jamais)
     * @return le job créé avec le statut {@code PENDING}
     */
    @PostMapping("/users/{userId}/batch-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_BATCH')")
    public BatchJobResponse create(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int failureRate
    ) {
        String id = UUID.randomUUID().toString();
        List<String> references = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            references.add("USER-" + userId + "-FAC-" + String.format("%03d", i));
        }

        BatchJobResponse job = BatchJobResponse.builder()
                .jobId(id)
                .userId(userId)
                .status(BatchJobStatus.PENDING)
                .requestedCount(references.size())
                .createdAt(Instant.now())
                .attempt(1)
                .maxAttempts(3)
                .files(List.of())
                .build();

        store.save(job);
        rabbitTemplate.convertAndSend(
                RabbitQueues.BATCH_JOBS,
                new BatchJobMessage(id, userId, references, 1, 3, failureRate)
        );

        return job;
    }

    /**
     * Retourne l'état d'un job par son identifiant.
     *
     * @param jobId l'identifiant unique du job
     * @return {@code 200 OK} avec le job, ou {@code 404} s'il est introuvable
     */
    @GetMapping("/batch-jobs/{jobId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_BATCH')")
    public ResponseEntity<BatchJobResponse> get(@PathVariable String jobId) {
        BatchJobResponse job = store.find(jobId);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }

    /**
     * Retourne tous les jobs batch d'un utilisateur donné.
     *
     * @param userId l'identifiant de l'utilisateur
     * @return la liste des jobs de l'utilisateur
     */
    @GetMapping("/users/{userId}/batch-jobs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_BATCH')")
    public List<BatchJobResponse> byUser(@PathVariable Long userId) {
        return store.findByUser(userId);
    }

    /**
     * Retourne des statistiques globales sur tous les jobs (par statut + total).
     *
     * @return une map statut → nombre de jobs
     */
    @GetMapping("/batch-jobs/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> stats() {
        return store.stats();
    }
}
