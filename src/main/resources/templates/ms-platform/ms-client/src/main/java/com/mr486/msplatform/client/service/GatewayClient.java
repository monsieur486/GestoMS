package com.mr486.msplatform.client.service;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import com.mr486.msplatform.client.security.SessionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Proxy BFF vers le backend via le gateway, avec le Bearer de la session.
 * Sur 401 : refresh (rotation) via ms-auth, mise à jour des deux tokens en session, et UN rejeu.
 */
@Service
public class GatewayClient {

    private final RestTemplate restTemplate;
    private final MsAuthClient msAuthClient;
    private final String gatewayUrl;

    public GatewayClient(RestTemplate restTemplate, MsAuthClient msAuthClient,
                         @Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.msAuthClient = msAuthClient;
        this.gatewayUrl = gatewayUrl;
    }

    /** GET {@code path} via le gateway avec l'access token de session ; refresh + rejeu sur 401. */
    public String get(HttpSession session, String path) {
        return exchangeWithRefresh(session, path, HttpMethod.GET, null);
    }

    /** POST {@code jsonBody} vers {@code path} via le gateway ; refresh + rejeu sur 401. */
    public String post(HttpSession session, String path, String jsonBody) {
        return exchangeWithRefresh(session, path, HttpMethod.POST, jsonBody);
    }

    /** PUT {@code jsonBody} vers {@code path} via le gateway ; refresh + rejeu sur 401. */
    public String put(HttpSession session, String path, String jsonBody) {
        return exchangeWithRefresh(session, path, HttpMethod.PUT, jsonBody);
    }

    /** DELETE {@code path} via le gateway ; refresh + rejeu sur 401. */
    public void delete(HttpSession session, String path) {
        exchangeWithRefresh(session, path, HttpMethod.DELETE, null);
    }

    private String exchangeWithRefresh(HttpSession session, String path, HttpMethod method, String body) {
        String accessToken = (String) session.getAttribute(SessionKeys.ACCESS_TOKEN);
        try {
            return doExchange(path, method, body, accessToken);
        } catch (UnauthorizedSignal first) {
            MsAuthTokens fresh;
            try {
                String refreshToken = (String) session.getAttribute(SessionKeys.REFRESH_TOKEN);
                fresh = msAuthClient.refresh(refreshToken);
            } catch (RuntimeException refreshFailed) {
                throw new SessionExpiredException();
            }
            session.setAttribute(SessionKeys.ACCESS_TOKEN, fresh.accessToken());
            session.setAttribute(SessionKeys.REFRESH_TOKEN, fresh.opaqueRefreshToken());
            try {
                return doExchange(path, method, body, fresh.accessToken());
            } catch (UnauthorizedSignal second) {
                throw new SessionExpiredException();
            }
        }
    }

    private String doExchange(String path, HttpMethod method, String body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken == null ? "" : accessToken);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    gatewayUrl + path, method, new HttpEntity<>(body, headers), String.class);
            return resp.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new UnauthorizedSignal();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new BackendForbiddenException();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            throw new BackendUnavailableException();
        }
    }

    /** Signal interne : 401 reçu (déclenche le refresh-retry). */
    private static class UnauthorizedSignal extends RuntimeException {}

    /** Levée lorsque la session a expiré et que le refresh a échoué (redirection vers /login). */
    public static class SessionExpiredException extends RuntimeException {}

    /** Levée lorsque le gateway retourne 403 Forbidden. */
    public static class BackendForbiddenException extends RuntimeException {}

    /** Levée lorsque le backend est injoignable ou retourne une erreur serveur. */
    public static class BackendUnavailableException extends RuntimeException {}
}
