package com.mr486.msplatform.consumer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration Redis du service consumer : fournit un {@link RedisTemplate} String/String
 * avec serializers uniformes pour les clés, valeurs et entrées de hash.
 */
@Configuration
public class RedisConfig {

    /**
     * Crée et configure un {@link RedisTemplate} String/String.
     *
     * @param factory la fabrique de connexions Redis
     * @return le template Redis configuré avec des serializers String
     */
    @Bean
    RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
