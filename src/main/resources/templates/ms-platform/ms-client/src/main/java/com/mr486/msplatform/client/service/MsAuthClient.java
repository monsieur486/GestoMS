package com.mr486.msplatform.client.service;

import com.mr486.msplatform.client.dto.MsAuthTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Client BFF vers ms-auth (exposé via le gateway). */
@Service
public class MsAuthClient {

    private final RestTemplate restTemplate;
    private final String gatewayUrl;

    public MsAuthClient(RestTemplate restTemplate, @Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
    }

    public MsAuthTokens login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        try {
            ResponseEntity<MsAuthTokens> resp =
                    restTemplate.postForEntity(gatewayUrl + "/auth/login", entity, MsAuthTokens.class);
            return resp.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new InvalidCredentialsException();
            }
            throw new AuthUnavailableException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AuthUnavailableException();
        }
    }

    public void logout(String accessToken, String opaqueRefreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                    Map.of("opaque_refresh_token", opaqueRefreshToken == null ? "" : opaqueRefreshToken), headers);
            restTemplate.postForEntity(gatewayUrl + "/auth/logout", entity, Void.class);
        } catch (Exception ignored) {
            // le logout local doit toujours réussir, même si ms-auth est injoignable
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {}

    public static class AuthUnavailableException extends RuntimeException {}
}
