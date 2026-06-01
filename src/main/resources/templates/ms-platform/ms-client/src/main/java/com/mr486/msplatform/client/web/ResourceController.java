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

/**
 * Contrôleur CRUD générique des ressources métier :
 * liste les entrées accessibles et délègue les opérations GET/POST au service via le gateway.
 */
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

    /**
     * Affiche la liste des ressources accessibles par l'utilisateur courant.
     *
     * @param authentication l'objet d'authentification Spring Security courant
     * @param model          le modèle Thymeleaf
     * @return le nom de la vue {@code resources}
     */
    @GetMapping
    public String index(Authentication authentication, Model model) {
        model.addAttribute("resources",
                ResourceAccess.accessible(clientProperties.resources(), authentication.getAuthorities()));
        return "resources";
    }

    /**
     * Affiche la liste des entités d'une ressource donnée en appelant le service via le gateway.
     *
     * @param serviceName    le nom du service cible (segment d'URL)
     * @param authentication l'objet d'authentification Spring Security courant
     * @param request        la requête HTTP (session utilisée pour le Bearer token)
     * @param model          le modèle Thymeleaf
     * @return la vue {@code resource}, ou une redirection si la ressource est inaccessible
     */
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

    /**
     * Crée une nouvelle entité dans le service cible via le gateway.
     *
     * @param serviceName    le nom du service cible (segment d'URL)
     * @param name           le nom de la nouvelle entité
     * @param description    la description de la nouvelle entité
     * @param authentication l'objet d'authentification Spring Security courant
     * @param request        la requête HTTP (session utilisée pour le Bearer token)
     * @return une redirection vers la liste, ou vers /login si la session a expiré
     */
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
