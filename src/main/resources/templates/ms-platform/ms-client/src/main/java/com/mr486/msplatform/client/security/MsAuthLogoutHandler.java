package com.mr486.msplatform.client.security;

import com.mr486.msplatform.client.service.MsAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Appelle ms-auth /auth/logout AVANT l'invalidation de session. Spring Security 6 exécute les
 * handlers ajoutés via addLogoutHandler avant le SecurityContextLogoutHandler interne (qui
 * invalide la session), donc la session est encore lisible ici.
 */
@Component
public class MsAuthLogoutHandler implements LogoutHandler {

    private final MsAuthClient msAuthClient;

    public MsAuthLogoutHandler(MsAuthClient msAuthClient) {
        this.msAuthClient = msAuthClient;
    }

    /**
     * Révoque les tokens auprès de ms-auth avant l'invalidation de la session HTTP.
     *
     * @param request        la requête HTTP courante (session encore accessible)
     * @param response       la réponse HTTP courante (non utilisée directement)
     * @param authentication l'objet d'authentification Spring Security courant
     */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
        if (accessToken != null) {
            msAuthClient.logout(accessToken, refreshToken);
        }
    }
}
