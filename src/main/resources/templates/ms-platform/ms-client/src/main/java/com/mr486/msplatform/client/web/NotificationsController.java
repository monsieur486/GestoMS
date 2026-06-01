package com.mr486.msplatform.client.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Contrôleur de la page de notifications :
 * expose l'URL publique du gateway pour que le navigateur puisse s'abonner au flux SSE.
 */
@Controller
public class NotificationsController {

    private final String gatewayPublicUrl;

    public NotificationsController(@Value("${gateway.public-url}") String gatewayPublicUrl) {
        this.gatewayPublicUrl = gatewayPublicUrl;
    }

    /**
     * Affiche la page de notifications et fournit l'URL publique du gateway au template.
     *
     * @param model le modèle Thymeleaf
     * @return le nom de la vue {@code notifications}
     */
    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("gatewayPublicUrl", gatewayPublicUrl);
        return "notifications";
    }
}
