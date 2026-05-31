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

@Controller
public class RolesController {

    private final KeycloakAdminClient keycloakAdminClient;

    public RolesController(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

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

    @PostMapping("/users/{id}/roles/add")
    public String add(@PathVariable String id, @RequestParam String roleName,
                      Authentication authentication) {
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            if (user != null && user.username().equals(authentication.getName())) {
                return "redirect:/users/" + id + "/roles?error=self";
            }
            keycloakAdminClient.addRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }

    @PostMapping("/users/{id}/roles/remove")
    public String remove(@PathVariable String id, @RequestParam String roleName,
                         Authentication authentication) {
        try {
            KeycloakUser user = keycloakAdminClient.getUser(id);
            if (user != null && user.username().equals(authentication.getName())) {
                return "redirect:/users/" + id + "/roles?error=self";
            }
            keycloakAdminClient.removeRealmRole(id, roleName);
            return "redirect:/users/" + id + "/roles";
        } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
            return "redirect:/users/" + id + "/roles?error";
        }
    }
}
