package com.mr486.msplatform.auth.controller;
import com.mr486.msplatform.auth.dto.*;
import com.mr486.msplatform.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contrôleur REST d'authentification : expose les endpoints de login,
 * de rafraîchissement de token et de déconnexion via Keycloak / ms-auth.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authentifie un utilisateur et retourne un access token JWT ainsi
     * qu'un refresh token opaque.
     *
     * @param request les identifiants de connexion (username / password)
     * @return la réponse contenant l'access token et le refresh token opaque
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Rafraîchit l'access token à partir d'un refresh token opaque valide.
     * Le refresh token opaque fourni est révoqué atomiquement et remplacé.
     *
     * @param request le refresh token opaque
     * @return la nouvelle paire access token / refresh token opaque
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * Déconnecte l'utilisateur : met en liste noire le JTI de l'access token
     * courant et révoque le refresh token Keycloak si fourni.
     *
     * @param request       le refresh token opaque à révoquer (optionnel)
     * @param authentication l'authentification JWT de la requête courante
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request,
                       Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        authService.logout(request, jwt);
    }
}
