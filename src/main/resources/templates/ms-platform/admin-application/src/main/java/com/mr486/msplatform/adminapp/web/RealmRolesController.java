package com.mr486.msplatform.adminapp.web;

import com.mr486.msplatform.adminapp.service.KeycloakAdminClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RealmRolesController {

    private static final String PROTECTED_ROLE = "ROLE_ADMIN";

    private final KeycloakAdminClient keycloakAdminClient;

    public RealmRolesController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/roles")
    public String list(Model model) {
        try {
            model.addAttribute("roles", keycloakAdminClient.listRealmRoles());
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            model.addAttribute("error", "Keycloak indisponible.");
        }
        return "realm-roles";
    }

    @PostMapping("/roles")
    public String create(@RequestParam String name,
                         @RequestParam(defaultValue = "") String description) {
        String trimmed = name.trim();
        if (trimmed.isBlank()) {
            return "redirect:/roles?error=blank";
        }
        try {
            keycloakAdminClient.createRealmRole(trimmed, description.trim());
            return "redirect:/roles?created";
        } catch (KeycloakAdminClient.RoleConflictException e) {
            return "redirect:/roles?error=conflict";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/roles?error";
        }
    }

    @PostMapping("/roles/{name}/delete")
    public String delete(@PathVariable String name) {
        if (PROTECTED_ROLE.equals(name)) {
            return "redirect:/roles?error=protected";
        }
        try {
            keycloakAdminClient.deleteRealmRole(name);
            return "redirect:/roles?deleted";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/roles?error";
        }
    }
}
