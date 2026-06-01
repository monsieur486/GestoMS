package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.dto.MsAuthTokens;
import com.mr486.msplatform.adminapp.security.JwtRoles;
import com.mr486.msplatform.adminapp.security.SessionKeys;
import com.mr486.msplatform.adminapp.service.MsAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Contrôleur gérant l'affichage et le traitement du formulaire de connexion via ms-auth.
 */
@Controller
public class LoginController {

    private final MsAuthClient msAuthClient;
    private final SecurityContextRepository securityContextRepository;

    /**
     * Construit le contrôleur en injectant le client ms-auth et le dépôt de contexte de sécurité.
     *
     * @param msAuthClient              le client BFF vers ms-auth
     * @param securityContextRepository le dépôt pour persister le contexte de sécurité en session
     */
    public LoginController(MsAuthClient msAuthClient, SecurityContextRepository securityContextRepository) {
        this.msAuthClient = msAuthClient;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Affiche le formulaire de connexion.
     *
     * @return le nom du template {@code login}
     */
    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    /**
     * Traite la soumission du formulaire de connexion : authentifie via ms-auth, régénère
     * la session et initialise le contexte de sécurité Spring.
     *
     * @param username le nom d'utilisateur saisi
     * @param password le mot de passe saisi
     * @param request  la requête HTTP (pour la gestion de session)
     * @param response la réponse HTTP (pour la persistance du contexte de sécurité)
     * @param model    le modèle Thymeleaf (pour les messages d'erreur)
     * @return une redirection vers {@code /} en cas de succès, ou le template {@code login} en cas d'erreur
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          Model model) {
        MsAuthTokens tokens;
        try {
            tokens = msAuthClient.login(username, password);
        } catch (MsAuthClient.InvalidCredentialsException e) {
            model.addAttribute("error", "Identifiants invalides");
            return "login";
        } catch (MsAuthClient.AuthUnavailableException e) {
            model.addAttribute("error", "Service d'authentification indisponible");
            return "login";
        }

        // Régénère l'identifiant de session à l'authentification (protection anti-fixation de session).
        HttpSession session = request.getSession(true);
        request.changeSessionId();

        List<SimpleGrantedAuthority> authorities = JwtRoles.realmRoles(tokens.accessToken()).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.setAttribute(SessionKeys.ACCESS_TOKEN, tokens.accessToken());
        session.setAttribute(SessionKeys.REFRESH_TOKEN, tokens.opaqueRefreshToken());

        return "redirect:/";
    }
}
