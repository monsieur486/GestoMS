package com.mr486.msplatform.consumer.messaging;

import com.mr486.msplatform.common.batch.BatchNotification;
import com.mr486.msplatform.common.constants.RabbitQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Écouteur AMQP qui reçoit les {@link BatchNotification} depuis la queue
 * {@code BATCH_NOTIFICATIONS} et les redistribue aux clients WebSocket abonnés
 * via les topics STOMP {@code /topic/batch} et {@code /topic/batch/<jobId>}.
 */
@Component
@RequiredArgsConstructor
public class BatchNotificationListener {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Reçoit une notification de fin de job et la diffuse en WebSocket.
     *
     * @param notification la notification contenant l'identifiant et le statut du job
     */
    @RabbitListener(queues = RabbitQueues.BATCH_NOTIFICATIONS)
    public void onNotification(BatchNotification notification) {
        messagingTemplate.convertAndSend("/topic/batch", notification);
        messagingTemplate.convertAndSend("/topic/batch/" + notification.getJobId(), notification);
    }
}
