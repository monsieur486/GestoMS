package com.mr486.msplatform.webui.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit en mémoire les utilisateurs connectés au chat (une entrée par session STOMP).
 * Plusieurs onglets d'un même utilisateur comptent comme un seul connecté.
 */
@Service
public class PresenceService {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    /**
     * Enregistre une session STOMP connectée pour un utilisateur.
     *
     * @param sessionId l'identifiant de session STOMP
     * @param username  le nom de l'utilisateur authentifié
     */
    public void add(String sessionId, String username) {
        sessions.put(sessionId, username);
    }

    /**
     * Retire une session déconnectée (no-op si la session est inconnue).
     *
     * @param sessionId l'identifiant de session STOMP
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Retourne la liste des utilisateurs connectés, distincts et triés alphabétiquement.
     *
     * @return les usernames connectés, jamais {@code null}
     */
    public List<String> connectedUsers() {
        return new ArrayList<>(new TreeSet<>(sessions.values()));
    }
}
