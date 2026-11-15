package com.sh.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests the gateway's security rules and circuit-breaker fallback over real HTTP.
 * <p>
 * Keycloak is not needed: the JwtDecoder is replaced by a mock that accepts any token.
 * The downstream services point at port 1, where nothing listens, so every routed call fails
 * with "connection refused", which is how a crashed service looks to the gateway.
 */
// Spring Boot switches metrics export off in tests by default; turn it back on so that
// /actuator/prometheus exists and the security rule for it can be tested.
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"order.service.url=http://localhost:1",
		"product.service.url=http://localhost:1",
		"inventory.service.url=http://localhost:1"
})
class ApiGatewayApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void acceptAnyToken() {
		when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("test-token")
				.header("alg", "none")
				.subject("test-client")
				.build());
	}

	@Test
	void requestWithoutTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.exchange("/api/product", HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void postToDownServiceReturns503FromFallback() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("test-token");
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.exchange("/api/order", HttpMethod.POST,
				new HttpEntity<>("{}", headers), String.class);

		// The fallback route used to be GET-only. A failed POST was forwarded to it as a POST,
		// matched nothing, and the client got 404 instead of 503.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).contains("Service Unavailable");
	}

	@Test
	void healthAndPrometheusArePublic() {
		assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(restTemplate.getForEntity("/actuator/prometheus", String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
	}
}
