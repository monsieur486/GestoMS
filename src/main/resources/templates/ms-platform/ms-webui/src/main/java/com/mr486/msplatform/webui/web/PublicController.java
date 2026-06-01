package com.mr486.msplatform.webui.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicController {

    @GetMapping("/public")
    public String publicPage() {
        return "public";
    }
}
