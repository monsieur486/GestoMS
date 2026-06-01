package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.dto.KeycloakRole;
import com.mr486.msplatform.adminapp.dto.KeycloakUser;
import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contrôleur gérant l'affichage et la modification des rôles realm d'un utilisateur Keycloak.
 */
@Controller
public class RolesController {

    private final KeycloakAdminClient keycloakAdminClient;

    /**
     * Construit le contrôleur en injectant le client Keycloak Admin.
     *
     * @param keycloakAdminClient le client d'accès à la Keycloak Admin REST API
     */
    public RolesController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Affiche la page de gestion des rôles d'un utilisateur (rôles assignés et assignables).
     *
     * @param id             l'identifiant Keycloak de l'utilisateur
     * @param authentication le contexte d'authentification de l'administrateur connecté
     * @param model          le modèle Thymeleaf
     * @return le nom du template {@code roles}
     */
    @GetMapping("/users/{id}/roles")
    public String roles(@PathVariable String id, Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        model.addAttribute("userId", id);
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            List<KeycloakRole> userRoles = keycloakAdminClient.listUserRealmRoles(id);
            Set<String> assigned = userRoles.stream().map(KeycloakRole::name).collect(Collectors.toSet());
            List<KeycloakRole> assignable = keycloakAdminClient.listRealmRoles().stream()
                    .filter(r -> !assigned.contains(r.name()))
                    .toList();
            model.addAttribute("user", user);
            model.addAttribute("userRoles", userRoles);
            model.addAttribute("assignableRoles", assignable);
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "roles";
    }

    /**
     * Assigne un rôle realm à un utilisateur.
     *
     * @param id       l'identifiant Keycloak de l'utilisateur
     * @param roleName le nom du rôle à assigner
     * @return une redirection vers la page de rôles de l'utilisateur
     */
    @PostMapping("/users/{id}/roles/add")
    public String add(@PathVariable String id, @RequestParam String roleName) {
        try {
            keycloakAdminClient.addRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }

    /**
     * Retire un rôle realm d'un utilisateur.
     *
     * @param id       l'identifiant Keycloak de l'utilisateur
     * @param roleName le nom du rôle à retirer
     * @return une redirection vers la page de rôles de l'utilisateur
     */
    @PostMapping("/users/{id}/roles/remove")
    public String remove(@PathVariable String id, @RequestParam String roleName) {
        try {
            keycloakAdminClient.removeRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }
}
