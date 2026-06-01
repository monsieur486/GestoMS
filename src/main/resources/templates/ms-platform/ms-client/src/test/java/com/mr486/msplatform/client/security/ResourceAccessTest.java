package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitaires de {@link ResourceAccess} : filtrage du catalogue selon les rôles ADMIN et utilisateur. */
class ResourceAccessTest {

    private static final List<ResourceEntry> CATALOG = List.of(
            new ResourceEntry("order-service", "/api/orders", "Order", "USER_ORDER_SERVICE"),
            new ResourceEntry("product-service", "/api/products", "Product", "USER_PRODUCT_SERVICE"));

    @Test
    void admin_sees_all() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(result).hasSize(2);
    }

    @Test
    void user_sees_only_own_resource() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_USER_ORDER_SERVICE")));
        assertThat(result).extracting(ResourceEntry::serviceName).containsExactly("order-service");
    }

    @Test
    void user_without_matching_role_sees_nothing() {
        var result = ResourceAccess.accessible(CATALOG, List.of(new SimpleGrantedAuthority("ROLE_USER_BATCH")));
        assertThat(result).isEmpty();
    }

    @Test
    void find_returns_null_for_inaccessible_resource() {
        var auth = List.of(new SimpleGrantedAuthority("ROLE_USER_ORDER_SERVICE"));
        assertThat(ResourceAccess.find(CATALOG, auth, "product-service")).isNull();
        assertThat(ResourceAccess.find(CATALOG, auth, "order-service")).isNotNull();
    }
}
