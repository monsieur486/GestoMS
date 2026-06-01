package com.mr486.msplatform.webui.service;

import com.mr486.msplatform.webui.dto.MsAuthTokens;
import com.mr486.msplatform.webui.security.SessionKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link GatewayClient} : succès, refresh-retry sur 401,
 * expiration de session et cas d'erreur backend (403, indisponible).
 */
class GatewayClientTest {

    private static final String PATH = "/service-consumer/api/aggregate";
    private static final String URL = "http://gw" + PATH;

    private RestTemplate restTemplate;
    private MsAuthClient msAuthClient;
    private GatewayClient gatewayClient;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        msAuthClient = Mockito.mock(MsAuthClient.class);
        gatewayClient = new GatewayClient(restTemplate, msAuthClient, "http://gw");
        session = new MockHttpSession();
        session.setAttribute(SessionKeys.ACCESS_TOKEN, "old-access");
        session.setAttribute(SessionKeys.REFRESH_TOKEN, "old-refresh");
    }

    @Test
    void returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"service-a\":\"{}\"}"));
        assertThat(gatewayClient.get(session, PATH)).isEqualTo("{\"service-a\":\"{}\"}");
    }

    @Test
    void refreshes_and_retries_once_on_401_then_succeeds() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("OK"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.get(session, PATH)).isEqualTo("OK");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
        assertThat(session.getAttribute(SessionKeys.REFRESH_TOKEN)).isEqualTo("new-refresh");
    }

    @Test
    void session_expired_when_refresh_fails() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null));
        when(msAuthClient.refresh(any())).thenThrow(new MsAuthClient.AuthUnavailableException());

        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.SessionExpiredException.class);
    }

    @Test
    void session_expired_when_retry_still_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.SessionExpiredException.class);
    }

    @Test
    void forbidden_maps_to_backend_forbidden() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "403", null, null, null));
        assertThatThrownBy(() -> gatewayClient.get(session, PATH))
                .isInstanceOf(GatewayClient.BackendForbiddenException.class);
    }

    @Test
    void post_returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1}"));
        assertThat(gatewayClient.post(session, PATH, "{\"name\":\"x\"}")).isEqualTo("{\"id\":1}");
    }

    @Test
    void post_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("created"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.post(session, PATH, "{}")).isEqualTo("created");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }

    @Test
    void put_returns_body_on_success() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.PUT), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1,\"name\":\"updated\"}"));
        assertThat(gatewayClient.put(session, PATH, "{\"name\":\"updated\",\"description\":\"d\"}"))
                .isEqualTo("{\"id\":1,\"name\":\"updated\"}");
    }

    @Test
    void put_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.PUT), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.ok("updated"));
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        assertThat(gatewayClient.put(session, PATH, "{}")).isEqualTo("updated");
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }

    @Test
    void delete_succeeds() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(ResponseEntity.noContent().build());
        gatewayClient.delete(session, PATH); // no exception = success
    }

    @Test
    void delete_refreshes_and_retries_once_on_401() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "401", null, null, null))
                .thenReturn(ResponseEntity.noContent().build());
        when(msAuthClient.refresh("old-refresh"))
                .thenReturn(new MsAuthTokens("new-access", "new-refresh", 300));

        gatewayClient.delete(session, PATH);
        assertThat(session.getAttribute(SessionKeys.ACCESS_TOKEN)).isEqualTo("new-access");
    }
}
