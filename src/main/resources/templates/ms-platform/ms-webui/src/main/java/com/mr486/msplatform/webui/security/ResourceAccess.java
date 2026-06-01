package com.mr486.msplatform.webui.security;

import com.mr486.msplatform.webui.config.WebUiProperties.ResourceEntry;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Filtre le catalogue selon les rôles :
 * ADMIN voit tout ; sinon seulement les ressources dont l'utilisateur possède {@code ROLE_<role>}.
 */
public final class ResourceAccess {

    private ResourceAccess() {}

    /**
     * Retourne les entrées du catalogue accessibles par l'utilisateur selon ses rôles :
     * ADMIN voit tout, sinon seules les ressources dont {@code ROLE_<role>} est présent.
     *
     * @param entries     le catalogue complet des ressources
     * @param authorities les autorités Spring Security de l'utilisateur courant
     * @return la liste filtrée des ressources accessibles, jamais {@code null}
     */
    public static List<ResourceEntry> accessible(List<ResourceEntry> entries,
                                                 Collection<? extends GrantedAuthority> authorities) {
        if (entries == null) return List.of();
        boolean admin = hasAuthority(authorities, "ROLE_ADMIN");
        return entries.stream()
                .filter(e -> admin || hasAuthority(authorities, "ROLE_" + e.role()))
                .toList();
    }

    /**
     * Recherche une entrée accessible par nom de service.
     *
     * @param entries     le catalogue complet des ressources
     * @param authorities les autorités Spring Security de l'utilisateur courant
     * @param serviceName le nom du service recherché
     * @return l'entrée correspondante, ou {@code null} si absente ou inaccessible
     */
    public static ResourceEntry find(List<ResourceEntry> entries,
                                     Collection<? extends GrantedAuthority> authorities,
                                     String serviceName) {
        return accessible(entries, authorities).stream()
                .filter(e -> e.serviceName().equals(serviceName))
                .findFirst().orElse(null);
    }

    private static boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String role) {
        if (authorities == null) return false;
        for (GrantedAuthority a : authorities) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
