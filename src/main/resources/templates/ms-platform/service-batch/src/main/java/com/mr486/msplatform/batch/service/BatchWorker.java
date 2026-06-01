package com.mr486.msplatform.batch.service;

import com.mr486.msplatform.common.batch.BatchJobMessage;
import com.mr486.msplatform.common.batch.BatchJobResponse;
import com.mr486.msplatform.common.batch.BatchJobStatus;
import com.mr486.msplatform.common.batch.BatchNotification;
import com.mr486.msplatform.common.constants.RabbitQueues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Travailleur batch asynchrone : écoute la queue {@code BATCH_JOBS}, traite chaque
 * {@link BatchJobMessage} de façon concurrente (Reactor {@code flatMap} sur un scheduler
 * élastique) et publie une {@link BatchNotification} en fin de traitement.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchWorker {

    @Value("${batch.file-concurrency:5}")
    private int fileConcurrency;

    @Value("${batch.min-delay-ms:500}")
    private long minDelayMs;

    @Value("${batch.max-delay-ms:1500}")
    private long maxDelayMs;

    private final RedisJobStore store;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Reçoit un message de travail batch, exécute les traitements en parallèle selon
     * {@code batch.file-concurrency} et met à jour le job dans Redis.
     * En cas d'erreur simulée ou réelle, passe le job à l'état {@code DEAD} et republie.
     *
     * @param message le message décrivant le travail à effectuer
     * @throws Exception si une erreur non récupérable survient (remontée pour retry AMQP)
     */
    @RabbitListener(queues = RabbitQueues.BATCH_JOBS)
    public void handle(BatchJobMessage message) throws Exception {
        BatchJobResponse job = store.find(message.getJobId());
        if (job == null) {
            return;
        }

        try {
            job.setStatus(BatchJobStatus.PROCESSING);
            store.save(job);

            if (message.getFailureRate() > 0
                    && ThreadLocalRandom.current().nextInt(100) < message.getFailureRate()
                    && message.getAttempt() < message.getMaxAttempts()) {
                throw new RuntimeException("Simulated failure");
            }

            long start = System.currentTimeMillis();
            String instance = InetAddress.getLocalHost().getHostName();

            List<Map<String, Object>> items = Flux.fromIterable(message.getReferences())
                    .flatMap(reference -> Mono.fromCallable(() -> generateOne(reference, instance))
                                    .subscribeOn(Schedulers.boundedElastic()),
                            fileConcurrency)
                    .collectList()
                    .block();

            if (items == null) {
                items = List.of();
            }

            List<String> files = new ArrayList<>();
            for (Map<String, Object> item : items) {
                files.add(String.valueOf(item.get("fileName")));
            }

            double total = (System.currentTimeMillis() - start) / 1000.0;
            double roundedTotal = Math.round(total * 100.0) / 100.0;
            double average = items.stream()
                    .mapToLong(item -> ((Number) item.get("durationMillis")).longValue())
                    .average()
                    .orElse(0.0) / 1000.0;
            double roundedAverage = Math.round(average * 100.0) / 100.0;

            job.setStatus(BatchJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setFiles(files);
            job.setGeneratedCount(files.size());
            job.setTotalSeconds(roundedTotal);
            job.setAverageSeconds(roundedAverage);
            job.setInstance(instance);
            job.setAttempt(message.getAttempt());
            job.setMaxAttempts(message.getMaxAttempts());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("files", files);
            result.put("generatedCount", files.size());
            result.put("totalSeconds", job.getTotalSeconds());
            result.put("averageSeconds", job.getAverageSeconds());
            result.put("instance", instance);
            result.put("items", items);
            job.setResult(result);

            store.save(job);
            publishNotification(job);
        } catch (Exception ex) {
            log.error(
                    "event=BATCH_FAILED jobId={} attempt={} error={}",
                    message.getJobId(),
                    message.getAttempt(),
                    ex.getMessage()
            );
            job.setError(ex.getMessage());
            job.setStatus(BatchJobStatus.DEAD);
            job.setCompletedAt(Instant.now());
            store.save(job);
            publishNotification(job);
            throw ex;
        }
    }

    private Map<String, Object> generateOne(String reference, String instance) throws InterruptedException {
        long itemStart = System.currentTimeMillis();
        Thread.sleep(ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1));

        String fileName = reference + ".batch";

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reference", reference);
        item.put("fileName", fileName);
        item.put("durationMillis", System.currentTimeMillis() - itemStart);
        item.put("instance", instance);
        item.put("success", true);
        item.put("error", null);

        return item;
    }

    private void publishNotification(BatchJobResponse job) {
        
        if (job.getStatus() == BatchJobStatus.COMPLETED) {
            log.warn(
                    "event=BATCH_COMPLETED jobId={} totalSeconds={} averageSeconds={} generatedCount={} instance={}",
                    job.getJobId(),
                    job.getTotalSeconds(),
                    job.getAverageSeconds(),
                    job.getGeneratedCount(),
                    job.getInstance()
            );
        } else {
            log.warn(
                    "event=BATCH_STATUS jobId={} status={} generatedCount={} totalSeconds={} instance={}",
                    job.getJobId(),
                    job.getStatus(),
                    job.getGeneratedCount(),
                    job.getTotalSeconds(),
                    job.getInstance()
            );
        }

        rabbitTemplate.convertAndSend(
                RabbitQueues.BATCH_NOTIFICATIONS,
                BatchNotification.builder()
                        .jobId(job.getJobId())
                        .status(job.getStatus())
                        .generatedCount(job.getGeneratedCount())
                        .totalSeconds(job.getTotalSeconds())
                        .instance(job.getInstance())
                        .build()
        );
    }
}
