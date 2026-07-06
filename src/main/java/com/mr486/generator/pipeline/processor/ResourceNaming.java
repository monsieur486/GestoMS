package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.ResourceModuleRequest;
import java.util.Locale;

/**
 * Noms dérivés d'une ressource métier, calculés une seule fois à partir de son
 * {@code serviceName}, {@code className} et préfixe de route effectif.
 *
 * <p>Centralise les formules de nommage jusqu'ici dupliquées entre
 * {@link ResourceExpandProcessor} et {@link CrossCuttingConfigProcessor} (casse serpent,
 * paquet concaténé, nom de rôle Keycloak, variable de jeton, URL passerelle…).
 *
 * <p><b>Exemple :</b> {@code ResourceNaming.from(res)} pour {@code order-service}/{@code Order}
 * donne {@code snake()="order_service"}, {@code scream()="ORDER_SERVICE"},
 * {@code servicePackage()="orderservice"}, {@code roleName()="USER_ORDER_SERVICE"}.
 */
public record ResourceNaming(String serviceName, String className, String routePrefix) {

    /**
     * Construit les noms dérivés à partir d'une requête de ressource.
     *
     * <p><b>Exemple :</b> {@code from(res)} lit {@code res.getServiceName()},
     * {@code res.getClassName()} et {@code res.getEffectiveRoutePrefix()}.
     *
     * @param res spécification de la ressource métier
     * @return les noms dérivés correspondants
     */
    public static ResourceNaming from(ResourceModuleRequest res) {
        return new ResourceNaming(res.getServiceName(), res.getClassName(), res.getEffectiveRoutePrefix());
    }

    /**
     * Nom de service en casse serpent ({@code order-service} → {@code order_service}).
     *
     * @return le nom de service en casse serpent
     */
    public String snake() {
        return serviceName.replace("-", "_");
    }

    /**
     * Nom de service en majuscules serpent ({@code ORDER_SERVICE}).
     *
     * @return le nom de service en majuscules serpent
     */
    public String scream() {
        return snake().toUpperCase(Locale.ROOT);
    }

    /**
     * Nom de classe de service en PascalCase ({@code order-service} → {@code OrderService}).
     *
     * @return le nom de classe de service en PascalCase
     */
    public String serviceClass() {
        return ProcessorUtils.toPascalCase(serviceName);
    }

    /**
     * Segment de paquet Java, minuscules concaténées ({@code order-service} → {@code orderservice}).
     *
     * @return le segment de paquet Java en minuscules concaténées
     */
    public String servicePackage() {
        return serviceName.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Nom d'entité en minuscules ({@code Order} → {@code order}).
     *
     * @return le nom d'entité en minuscules
     */
    public String entityLower() {
        return className.toLowerCase(Locale.ROOT);
    }

    /**
     * Nom d'entité pluriel en minuscules ({@code Order} → {@code orders}).
     *
     * @return le nom d'entité pluriel en minuscules
     */
    public String entityPlural() {
        return entityLower() + "s";
    }

    /**
     * Rôle Keycloak associé au service ({@code USER_ORDER_SERVICE}).
     *
     * @return le rôle Keycloak associé au service
     */
    public String roleName() {
        return "USER_" + scream();
    }

    /**
     * Nom de variable de jeton dans test-all.sh ({@code TOKEN_ORDER_SERVICE}).
     *
     * @return le nom de variable de jeton dans test-all.sh
     */
    public String tokenVar() {
        return "TOKEN_" + scream();
    }

    /**
     * Utilisateur de test Keycloak du service ({@code test-order-service}).
     *
     * @return l'utilisateur de test Keycloak du service
     */
    public String testUser() {
        return "test-" + serviceName;
    }

    /**
     * URL complète via la passerelle ({@code $GATEWAY_URL/order-service/api/orders}).
     *
     * @return l'URL complète via la passerelle
     */
    public String gatewayUrl() {
        return "$GATEWAY_URL/" + serviceName + routePrefix;
    }

    /**
     * Chemin routé sous la passerelle, sans hôte ({@code order-service/api/orders}).
     *
     * @return le chemin routé sous la passerelle, sans hôte
     */
    public String routePath() {
        return serviceName + routePrefix;
    }
}
