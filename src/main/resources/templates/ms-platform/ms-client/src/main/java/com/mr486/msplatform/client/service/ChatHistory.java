package com.mr486.msplatform.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.client.dto.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Historique du chat persisté en Redis : les 50 derniers messages (best-effort). */
@Service
public class ChatHistory {

    private static final String KEY = "chat:history";
    private static final long MAX = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatHistory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(ChatMessage message) {
        try {
            String json = mapper.writeValueAsString(message);
            redis.opsForList().rightPush(KEY, json);
            redis.opsForList().trim(KEY, -MAX, -1);
        } catch (Exception ignored) {
            // historique best-effort : ne bloque pas la diffusion live
        }
    }

    public List<ChatMessage> recent() {
        List<ChatMessage> result = new ArrayList<>();
        try {
            List<String> raw = redis.opsForList().range(KEY, 0, -1);
            if (raw != null) {
                for (String json : raw) {
                    result.add(mapper.readValue(json, ChatMessage.class));
                }
            }
        } catch (Exception ignored) {
            // Redis indisponible : page ouverte vide
        }
        return result;
    }
}
