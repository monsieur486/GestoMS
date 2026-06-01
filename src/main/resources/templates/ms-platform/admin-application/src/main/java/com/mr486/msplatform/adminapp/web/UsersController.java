package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contrôleur gérant la liste, la création, la modification, la suppression et
 * la réinitialisation du mot de passe des utilisateurs Keycloak.
 */
@Controller
public class UsersController {

    private final KeycloakAdminClient keycloakAdminClient;

    /**
     * Construit le contrôleur en injectant le client Keycloak Admin.
     *
     * @param keycloakAdminClient le client d'accès à la Keycloak Admin REST API
     */
    public UsersController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Affiche la liste paginée des utilisateurs avec un filtre de recherche optionnel.
     *
     * @param search         terme de recherche (username/email/prénom/nom), peut être {@code null}
     * @param page           numéro de page courant (0-indexé)
     * @param authentication le contexte d'authentification de l'administrateur connecté
     * @param model          le modèle Thymeleaf
     * @return le nom du template {@code users}
     */
    @GetMapping("/users")
    public String users(@RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") int page,
                        Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        int size = 20;
        int first = page * size;
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("page", page);
        try {
            var users = keycloakAdminClient.listUsers(search, first, size);
            int total = keycloakAdminClient.countUsers(search);
            int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
            model.addAttribute("users", users);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasPrev", page > 0);
            model.addAttribute("hasNext", (page + 1) < totalPages);
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "users";
    }

    /**
     * Crée un nouvel utilisateur Keycloak à partir des paramètres du formulaire.
     *
     * @param username  le nom d'utilisateur
     * @param email     l'adresse e-mail (optionnel)
     * @param firstName le prénom (optionnel)
     * @param lastName  le nom de famille (optionnel)
     * @param password  le mot de passe initial
     * @return une redirection vers {@code /users} en cas de succès ou avec un paramètre d'erreur
     */
    @PostMapping("/users")
    public String create(@RequestParam String username,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String firstName,
                         @RequestParam(required = false) String lastName,
                         @RequestParam String password) {
        try {
            keycloakAdminClient.createUser(username, email, firstName, lastName, password);
            return "redirect:/users";
        } catch (KeycloakAdminClient.UserConflictException e) {
            return "redirect:/users?error=conflict";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }

    /**
     * Supprime un utilisateur Keycloak par son identifiant.
     *
     * @param id l'identifiant Keycloak de l'utilisateur à supprimer
     * @return une redirection vers {@code /users}
     */
    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable String id) {
        try {
            keycloakAdminClient.deleteUser(id);
            return "redirect:/users";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }

    /**
     * Affiche le formulaire de modification d'un utilisateur.
     *
     * @param id             l'identifiant Keycloak de l'utilisateur
     * @param authentication le contexte d'authentification de l'administrateur connecté
     * @param model          le modèle Thymeleaf
     * @return le nom du template {@code edit}
     */
    @GetMapping("/users/{id}/edit")
    public String editForm(@PathVariable String id, Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        try {
            model.addAttribute("user", keycloakAdminClient.getUser(id));
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "edit";
    }

    /**
     * Applique les modifications d'un utilisateur (email, prénom, nom, état actif).
     *
     * @param id        l'identifiant Keycloak de l'utilisateur
     * @param email     la nouvelle adresse e-mail (optionnel)
     * @param firstName le nouveau prénom (optionnel)
     * @param lastName  le nouveau nom de famille (optionnel)
     * @param enabled   {@code true} pour activer le compte, {@code false} pour le désactiver
     * @return une redirection vers le formulaire d'édition avec le statut de l'opération
     */
    @PostMapping("/users/{id}/edit")
    public String edit(@PathVariable String id,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String firstName,
                       @RequestParam(required = false) String lastName,
                       @RequestParam(defaultValue = "false") boolean enabled) {
        try {
            keycloakAdminClient.updateUser(id, email, firstName, lastName, enabled);
            return "redirect:/users/" + id + "/edit?updated";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }

    /**
     * Réinitialise le mot de passe d'un utilisateur Keycloak.
     *
     * @param id       l'identifiant Keycloak de l'utilisateur
     * @param password le nouveau mot de passe
     * @return une redirection vers le formulaire d'édition avec le statut de l'opération
     */
    @PostMapping("/users/{id}/password")
    public String resetPassword(@PathVariable String id, @RequestParam String password) {
        try {
            keycloakAdminClient.resetPassword(id, password);
            return "redirect:/users/" + id + "/edit?pwd";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }
}
