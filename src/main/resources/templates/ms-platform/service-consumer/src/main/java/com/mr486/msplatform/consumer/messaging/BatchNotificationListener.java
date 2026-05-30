package com.mr486.msplatform.consumer.messaging;

import com.mr486.msplatform.common.batch.BatchNotification;
import com.mr486.msplatform.common.constants.RabbitQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchNotificationListener {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitQueues.BATCH_NOTIFICATIONS)
    public void onNotification(BatchNotification notification) {
        messagingTemplate.convertAndSend("/topic/batch", notification);
        messagingTemplate.convertAndSend("/topic/batch/" + notification.getJobId(), notification);
    }
}
