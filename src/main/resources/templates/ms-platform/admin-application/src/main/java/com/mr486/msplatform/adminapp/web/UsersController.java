package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UsersController {

    private final KeycloakAdminClient keycloakAdminClient;

    public UsersController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/users")
    public String users(Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        try {
            model.addAttribute("users", keycloakAdminClient.listUsers());
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "users";
    }

    @PostMapping("/users")
    public String create(@RequestParam String username, @RequestParam(required = false) String email,
                         @RequestParam(required = false) String firstName, @RequestParam(required = false) String lastName,
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

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable String id) {
        try {
            keycloakAdminClient.deleteUser(id);
            return "redirect:/users";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users?error";
        }
    }

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

    @PostMapping("/users/{id}/edit")
    public String edit(@PathVariable String id, @RequestParam(required = false) String email,
                       @RequestParam(required = false) String firstName, @RequestParam(required = false) String lastName,
                       @RequestParam(defaultValue = "false") boolean enabled) {
        try {
            keycloakAdminClient.updateUser(id, email, firstName, lastName, enabled);
            return "redirect:/users/" + id + "/edit?updated";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/edit?error";
        }
    }

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
