package com.mr486.msplatform.auth.configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de la sécurité Spring Security pour {@code ms-auth} :
 * désactive CSRF, ouvre les routes de login/refresh et les actuators,
 * et active la validation JWT via le serveur de ressources OAuth2.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité HTTP.
     * <p>
     * Règles appliquées :
     * <ul>
     *   <li>{@code /auth/login}, {@code /auth/refresh} et {@code /actuator/**} sont accessibles
     *       sans authentification.</li>
     *   <li>Toutes les autres requêtes nécessitent un JWT valide.</li>
     * </ul>
     *
     * @param http le constructeur de configuration {@link HttpSecurity}
     * @return la {@link SecurityFilterChain} configurée
     * @throws Exception si la configuration échoue
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/auth/login", "/auth/refresh", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }
}
