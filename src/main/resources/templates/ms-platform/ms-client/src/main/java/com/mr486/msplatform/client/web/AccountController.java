package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.config.ClientProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AccountController {

    private final ClientProperties clientProperties;

    public AccountController(ClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        model.addAttribute("roles", roles);
        model.addAttribute("keycloakAccountUrl", clientProperties.keycloakAccountUrl());
        return "account";
    }
}
