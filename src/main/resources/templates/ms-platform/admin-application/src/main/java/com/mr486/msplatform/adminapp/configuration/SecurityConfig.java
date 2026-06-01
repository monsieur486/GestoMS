package com.mr486.msplatform.adminapp.configuration;

import com.mr486.msplatform.adminapp.security.MsAuthLogoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Configuration de la sécurité Spring Security : protection des routes, gestion de session
 * et déconnexion via {@link MsAuthLogoutHandler}.
 */
@Configuration
public class SecurityConfig {

    /**
     * Déclare le dépôt de contexte de sécurité basé sur la session HTTP.
     *
     * @return une instance de {@link HttpSessionSecurityContextRepository}
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Construit la chaîne de filtres de sécurité : autorise {@code /login} et les ressources
     * statiques, exige le rôle {@code ADMIN} pour le reste, et configure la déconnexion.
     *
     * @param http                      le constructeur de configuration HTTP
     * @param logoutHandler             le handler de déconnexion qui révoque le token ms-auth
     * @param securityContextRepository le dépôt de contexte de sécurité
     * @return la {@link SecurityFilterChain} configurée
     * @throws Exception en cas d'erreur de configuration Spring Security
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           MsAuthLogoutHandler logoutHandler,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(logoutHandler)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION"));
        return http.build();
    }
}
