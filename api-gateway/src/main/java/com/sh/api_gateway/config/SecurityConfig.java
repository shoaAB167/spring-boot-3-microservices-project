package com.sh.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The gateway is an OAuth2 <b>resource server</b>: it does not log users in itself. It only
 * checks that each request carries a valid JWT access token issued by Keycloak. The signature,
 * expiry and issuer are verified with Keycloak's public keys, which Spring downloads from the
 * issuer-uri in application.properties.
 */
@Configuration
public class SecurityConfig {

    /**
     * Endpoints reachable without a token. Keep this list as short as possible.
     * Every pattern must start with "/". The old pattern "*" + "/actuator/prometheus" never
     * matched, so Prometheus got 401 Unauthorized when it scraped the gateway.
     */
    private static final String[] PUBLIC_URLS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/aggregate/**",
            "/actuator/health", "/actuator/health/**", "/actuator/prometheus"
    };

    /** Browser origins allowed to call the API (e.g. a React/Angular front end). Never "*". */
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * The single source of CORS rules. There used to be a second CORS config (a
     * WebMvcConfigurer that allowed every origin). Spring Security's CorsFilter runs first,
     * so with two configs it was unclear which rules actually applied.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
