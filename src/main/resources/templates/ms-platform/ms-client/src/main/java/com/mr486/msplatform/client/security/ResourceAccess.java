package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/** Filtre le catalogue selon les rôles : ADMIN voit tout ; sinon seulement les resources dont l'utilisateur a ROLE_<role>. */
public final class ResourceAccess {

    private ResourceAccess() {}

    public static List<ResourceEntry> accessible(List<ResourceEntry> entries,
                                                 Collection<? extends GrantedAuthority> authorities) {
        if (entries == null) return List.of();
        boolean admin = hasAuthority(authorities, "ROLE_ADMIN");
        return entries.stream()
                .filter(e -> admin || hasAuthority(authorities, "ROLE_" + e.role()))
                .toList();
    }

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
