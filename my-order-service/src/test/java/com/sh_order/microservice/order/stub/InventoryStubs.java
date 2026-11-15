package com.sh_order.microservice.order.stub;

import lombok.experimental.UtilityClass;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stubs that pretend to be inventory-service, so order-service can be tested
 * on its own (no real inventory-service or inventory database needed).
 */
@UtilityClass
public class InventoryStubs {

    public void stubInventoryCall(String skuCode, Integer quantity, boolean inStock) {
        stubFor(get(urlPathEqualTo("/api/inventory"))
                .withQueryParam("skuCode", equalTo(skuCode))
                .withQueryParam("quantity", equalTo(quantity.toString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Boolean.toString(inStock))));
    }

    /** inventory-service is broken: every call returns 500. */
    public void stubInventoryFailure() {
        stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(aResponse().withStatus(500)));
    }
}
