package com.mr486.msplatform.adminapp.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Contrôleur de la page d'accueil de l'application d'administration.
 */
@Controller
public class HomeController {

    /**
     * Affiche la page d'accueil avec le nom d'utilisateur et les rôles de l'utilisateur connecté.
     *
     * @param authentication le contexte d'authentification Spring Security
     * @param model          le modèle Thymeleaf
     * @return le nom du template {@code home}
     */
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        model.addAttribute("roles", roles);
        return "home";
    }
}
