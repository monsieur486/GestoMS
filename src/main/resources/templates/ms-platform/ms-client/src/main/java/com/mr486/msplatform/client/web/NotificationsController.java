package com.mr486.msplatform.client.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationsController {

    private final String gatewayPublicUrl;

    public NotificationsController(@Value("${gateway.public-url}") String gatewayPublicUrl) {
        this.gatewayPublicUrl = gatewayPublicUrl;
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("gatewayPublicUrl", gatewayPublicUrl);
        return "notifications";
    }
}
