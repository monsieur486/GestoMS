package com.mr486.msplatform.webui.configuration;

import com.mr486.msplatform.webui.security.MsAuthLogoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Configuration de la sécurité Spring Security du client BFF :
 * session HTTP, CSRF partiel, règles d'accès et déconnexion via ms-auth.
 */
@Configuration
public class SecurityConfig {

    /**
     * Déclare le dépôt de contexte de sécurité basé sur la session HTTP.
     *
     * @return un {@link HttpSessionSecurityContextRepository}
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Construit la chaîne de filtres de sécurité :
     * CSRF ignoré pour {@code /ws/**}, accès public à {@code /login} et aux ressources statiques,
     * {@code /consumer} réservé aux administrateurs, déconnexion via {@link MsAuthLogoutHandler}.
     *
     * @param http                      le configurateur Spring Security
     * @param logoutHandler             le handler qui révoque les tokens côté ms-auth
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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/public", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers("/consumer").hasRole("ADMIN")
                        .anyRequest().authenticated())
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
