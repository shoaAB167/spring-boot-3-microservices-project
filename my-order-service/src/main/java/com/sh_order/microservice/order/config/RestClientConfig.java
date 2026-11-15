package com.sh_order.microservice.order.config;

import com.sh_order.microservice.order.client.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Creates the {@link InventoryClient} implementation.
 * <p>
 * We use the auto-configured {@link RestClient.Builder} instead of {@code RestClient.builder()}
 * because Spring Boot has already attached the ObservationRegistry to it, so every outgoing
 * call gets metrics and passes the trace id on to inventory-service (distributed tracing).
 */
@Configuration
public class RestClientConfig {

    @Value("${inventory.url}")
    private String inventoryServiceUrl;

    /** Time allowed to open a TCP connection to inventory-service. */
    @Value("${inventory.client.connect-timeout:1s}")
    private Duration connectTimeout;

    /** Time allowed to wait for the response. Without a timeout a hung service hangs us too. */
    @Value("${inventory.client.read-timeout:1s}")
    private Duration readTimeout;

    @Bean
    public InventoryClient inventoryClient(RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);

        RestClient restClient = restClientBuilder
                .baseUrl(inventoryServiceUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        var httpServiceProxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return httpServiceProxyFactory.createClient(InventoryClient.class);
    }
}
