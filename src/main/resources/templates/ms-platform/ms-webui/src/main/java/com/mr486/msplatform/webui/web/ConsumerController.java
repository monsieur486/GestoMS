package com.mr486.msplatform.webui.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mr486.msplatform.webui.service.GatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôleur de la vue d'agrégation consommateur :
 * appelle {@code /service-consumer/api/aggregate} via le gateway et affiche les réponses JSON.
 */
@Controller
public class ConsumerController {

    private final GatewayClient gatewayClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsumerController(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    /**
     * Affiche la page consommateur avec le résultat agrégé de tous les services.
     *
     * @param request la requête HTTP (session utilisée pour le Bearer token)
     * @param model   le modèle Thymeleaf
     * @return la vue {@code consumer}, ou une redirection si la session a expiré
     */
    @GetMapping("/consumer")
    public String consumer(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        try {
            String json = gatewayClient.get(session, "/service-consumer/api/aggregate");
            Map<String, String> aggregate = mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            Map<String, String> services = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : aggregate.entrySet()) {
                services.put(e.getKey(), prettyOrRaw(e.getValue()));
            }
            model.addAttribute("services", services);
            return "consumer";
        } catch (GatewayClient.SessionExpiredException e) {
            return "redirect:/login?expired";
        } catch (GatewayClient.BackendForbiddenException e) {
            model.addAttribute("error", "Accès refusé par le service (réservé aux administrateurs).");
            return "consumer";
        } catch (Exception e) {
            model.addAttribute("error", "Service indisponible.");
            return "consumer";
        }
    }

    private String prettyOrRaw(String json) {
        try {
            Object parsed = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }
}
