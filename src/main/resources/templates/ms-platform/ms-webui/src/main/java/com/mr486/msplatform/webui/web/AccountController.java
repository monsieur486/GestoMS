package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.config.WebUiProperties;
import com.mr486.msplatform.webui.dto.MsAuthTokens;
import com.mr486.msplatform.webui.security.SessionKeys;
import com.mr486.msplatform.webui.service.MsAuthClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class AccountController {

    private final WebUiProperties webUiProperties;
    private final MsAuthClient msAuthClient;

    public AccountController(WebUiProperties webUiProperties, MsAuthClient msAuthClient) {
        this.webUiProperties = webUiProperties;
        this.msAuthClient = msAuthClient;
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        model.addAttribute("roles", roles);
        model.addAttribute("keycloakAccountUrl", webUiProperties.keycloakAccountUrl());
        return "account";
    }

    /**
     * Change le mot de passe de l'utilisateur courant après validation de la correspondance.
     *
     * @param oldPassword l'ancien mot de passe
     * @param newPassword le nouveau mot de passe
     * @param confirm     la confirmation du nouveau mot de passe (doit correspondre)
     * @param session     la session HTTP (porte l'access et le refresh token)
     * @return une redirection vers {@code /account} avec le statut de l'opération
     */
    @PostMapping("/account/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirm,
                                 HttpSession session) {
        if (!newPassword.equals(confirm)) {
            return "redirect:/account?mismatch";
        }
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        try {
            try {
                msAuthClient.changePassword(accessToken, oldPassword, newPassword);
            } catch (MsAuthClient.TokenExpiredException expired) {
                String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
                MsAuthTokens fresh = msAuthClient.refresh(refreshToken);
                session.setAttribute(SessionKeys.ACCESS_TOKEN, fresh.accessToken());
                session.setAttribute(SessionKeys.REFRESH_TOKEN, fresh.opaqueRefreshToken());
                msAuthClient.changePassword(fresh.accessToken(), oldPassword, newPassword);
            }
            return "redirect:/account?ok";
        } catch (MsAuthClient.WrongOldPasswordException e) {
            return "redirect:/account?wrong";
        } catch (RuntimeException e) {
            return "redirect:/account?error";
        }
    }
}
