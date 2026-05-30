package com.mr486.msplatform.client.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.client.config.ClientProperties;
import com.mr486.msplatform.client.config.ClientProperties.ResourceEntry;
import com.mr486.msplatform.client.security.ResourceAccess;
import com.mr486.msplatform.client.service.GatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/resources")
public class ResourceController {

    private final GatewayClient gatewayClient;
    private final ClientProperties clientProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResourceController(GatewayClient gatewayClient, ClientProperties clientProperties) {
        this.gatewayClient = gatewayClient;
        this.clientProperties = clientProperties;
    }

    @GetMapping
    public String index(Authentication authentication, Model model) {
        model.addAttribute("resources",
                ResourceAccess.accessible(clientProperties.resources(), authentication.getAuthorities()));
        return "resources";
    }

    @GetMapping("/{serviceName}")
    public String list(@PathVariable String serviceName, Authentication authentication,
                       HttpServletRequest request, Model model) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        model.addAttribute("entry", entry);
        HttpSession session = request.getSession(false);
        try {
            String json = gatewayClient.get(session, "/" + entry.serviceName() + entry.routePrefix());
            List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            model.addAttribute("rows", rows);
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            model.addAttribute("error", "Service indisponible.");
        }
        return "resource";
    }

    @PostMapping("/{serviceName}")
    public String create(@PathVariable String serviceName, @RequestParam String name,
                         @RequestParam String description, Authentication authentication,
                         HttpServletRequest request) {
        ResourceEntry entry = ResourceAccess.find(
                clientProperties.resources(), authentication.getAuthorities(), serviceName);
        if (entry == null) {
            return "redirect:/resources";
        }
        HttpSession session = request.getSession(false);
        try {
            String body = mapper.writeValueAsString(Map.of("name", name, "description", description));
            gatewayClient.post(session, "/" + entry.serviceName() + entry.routePrefix(), body);
            return "redirect:/resources/" + serviceName;
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (Exception e) {
            return "redirect:/resources/" + serviceName + "?error";
        }
    }
}
