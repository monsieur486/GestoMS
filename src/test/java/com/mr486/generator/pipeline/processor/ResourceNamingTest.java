package com.mr486.generator.pipeline.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.ResourceModuleRequest;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de {@link ResourceNaming} : chaque dérivé reproduit à l'identique la formule
 * des helpers de nommage historiques (roleName/tokenVar/testUser/gatewayUrl/routePath et les
 * substitutions de {@code applyBaseReplacements}).
 */
class ResourceNamingTest {

    private static ResourceModuleRequest resource(String serviceName, String className, String routePrefix) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(serviceName);
        r.setClassName(className);
        r.setRoutePrefix(routePrefix);
        return r;
    }

    @Test
    void derives_all_names_for_kebab_service() {
        ResourceNaming n = ResourceNaming.from(resource("order-service", "Order", null));

        assertThat(n.serviceName()).isEqualTo("order-service");
        assertThat(n.className()).isEqualTo("Order");
        assertThat(n.snake()).isEqualTo("order_service");
        assertThat(n.scream()).isEqualTo("ORDER_SERVICE");
        assertThat(n.serviceClass()).isEqualTo("OrderService");
        assertThat(n.servicePackage()).isEqualTo("orderservice");
        assertThat(n.entityLower()).isEqualTo("order");
        assertThat(n.entityPlural()).isEqualTo("orders");
        assertThat(n.roleName()).isEqualTo("USER_ORDER_SERVICE");
        assertThat(n.tokenVar()).isEqualTo("TOKEN_ORDER_SERVICE");
        assertThat(n.testUser()).isEqualTo("test-order-service");
    }

    @Test
    void derives_route_dependent_names_from_default_prefix() {
        // routePrefix null → dérivé /api/{classNameLower}s par ResourceModuleRequest
        ResourceNaming n = ResourceNaming.from(resource("order-service", "Order", null));

        assertThat(n.routePrefix()).isEqualTo("/api/orders");
        assertThat(n.gatewayUrl()).isEqualTo("$GATEWAY_URL/order-service/api/orders");
        assertThat(n.routePath()).isEqualTo("order-service/api/orders");
    }

    @Test
    void honours_explicit_route_prefix() {
        ResourceNaming n = ResourceNaming.from(resource("order-service", "Order", "/orders"));

        assertThat(n.gatewayUrl()).isEqualTo("$GATEWAY_URL/order-service/orders");
        assertThat(n.routePath()).isEqualTo("order-service/orders");
    }
}
