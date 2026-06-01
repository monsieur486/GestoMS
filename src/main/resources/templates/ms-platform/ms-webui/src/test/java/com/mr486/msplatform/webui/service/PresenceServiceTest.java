package com.mr486.msplatform.webui.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link PresenceService} : liste distincte triée, dédoublonnage
 * multi-sessions d'un même utilisateur, et retrait au dernier disconnect.
 */
class PresenceServiceTest {

    @Test
    void lists_connected_users_distinct_and_sorted() {
        PresenceService presence = new PresenceService();
        presence.add("s1", "bob");
        presence.add("s2", "alice");
        assertThat(presence.connectedUsers()).containsExactly("alice", "bob");
    }

    @Test
    void dedupes_same_user_across_sessions_and_removes_on_last() {
        PresenceService presence = new PresenceService();
        presence.add("s1", "alice");
        presence.add("s2", "alice");
        assertThat(presence.connectedUsers()).containsExactly("alice");

        presence.remove("s1");
        assertThat(presence.connectedUsers()).containsExactly("alice"); // encore connecté via s2

        presence.remove("s2");
        assertThat(presence.connectedUsers()).isEmpty();
    }

    @Test
    void remove_unknown_session_is_noop() {
        PresenceService presence = new PresenceService();
        presence.remove("ghost");
        assertThat(presence.connectedUsers()).isEmpty();
    }
}
