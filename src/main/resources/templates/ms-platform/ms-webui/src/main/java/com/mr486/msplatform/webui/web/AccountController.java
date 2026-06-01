package com.mr486.msplatform.webui.web;

import com.mr486.msplatform.webui.config.WebUiProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AccountController {

    private final WebUiProperties webUiProperties;

    public AccountController(WebUiProperties webUiProperties) {
        this.webUiProperties = webUiProperties;
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
}
