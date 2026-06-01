package com.mr486.msplatform.batch.configuration;

import com.mr486.msplatform.common.constants.RabbitQueues;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration RabbitMQ du service batch : déclaration des queues durables,
 * du convertisseur JSON et du template d'envoi.
 */
@Configuration
public class RabbitConfig {

    /**
     * Déclare la queue durable des travaux batch ({@code BATCH_JOBS}).
     *
     * @return la queue {@code BATCH_JOBS}
     */
    @Bean
    Queue batchJobsQueue() {
        return new Queue(RabbitQueues.BATCH_JOBS, true);
    }

    /**
     * Déclare la queue durable des notifications batch ({@code BATCH_NOTIFICATIONS}).
     *
     * @return la queue {@code BATCH_NOTIFICATIONS}
     */
    @Bean
    Queue batchNotificationsQueue() {
        return new Queue(RabbitQueues.BATCH_NOTIFICATIONS, true);
    }

    /**
     * Fournit un convertisseur JSON Jackson pour les messages AMQP.
     *
     * @return un {@link Jackson2JsonMessageConverter}
     */
    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Fournit un {@link RabbitTemplate} configuré avec la connexion et le convertisseur JSON.
     *
     * @param connectionFactory la fabrique de connexions RabbitMQ
     * @param converter         le convertisseur JSON Jackson
     * @return le template RabbitMQ prêt à l'emploi
     */
    @Bean
    RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * Fournit une fabrique de conteneurs d'écoute RabbitMQ avec conversion JSON.
     *
     * @param connectionFactory la fabrique de connexions RabbitMQ
     * @param converter         le convertisseur JSON Jackson
     * @return la fabrique de conteneurs d'écoute configurée
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
