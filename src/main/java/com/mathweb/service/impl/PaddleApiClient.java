package com.mathweb.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mathweb.exception.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PaddleApiClient {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleApiClient.class);

    private final RestClient restClient;
    private final String checkoutUrl;

    public PaddleApiClient(
            RestClient.Builder builder,
            @Value("${paddle.api-key}") String apiKey,
            @Value("${paddle.api-base-url}") String apiBaseUrl,
            @Value("${paddle.checkout-url}") String checkoutUrl
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "PADDLE_API_KEY is missing"
            );
        }

        if (!"https://api.paddle.com".equals(apiBaseUrl)
                && !"https://sandbox-api.paddle.com".equals(apiBaseUrl)) {
            throw new IllegalStateException(
                    "Invalid Paddle API base URL"
            );
        }

        if (checkoutUrl == null
                || !checkoutUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "PADDLE_CHECKOUT_URL must be an HTTPS URL"
            );
        }

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        this.restClient = builder
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setBearerAuth(apiKey);
                    headers.set(
                            "Paddle-Version",
                            "1"
                    );
                    headers.set(
                            HttpHeaders.ACCEPT,
                            MediaType.APPLICATION_JSON_VALUE
                    );
                })
                .build();

        this.checkoutUrl = checkoutUrl;
    }

    public CreatedTransaction createCheckoutTransaction(
            UUID checkoutOrderId,
            String priceId
    ) {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        List.of(new TransactionItem(priceId, 1)),
                        "automatic",
                        Map.of(
                                "checkout_order_id",
                                checkoutOrderId.toString()
                        ),
                        new CheckoutRequest(checkoutUrl)
                );

        try {
            CreateTransactionResponse response = restClient
                    .post()
                    .uri("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CreateTransactionResponse.class);

            if (response == null
                    || response.data() == null
                    || isBlank(response.data().id())
                    || response.data().checkout() == null
                    || isBlank(response.data().checkout().url())) {
                throw new PaymentProviderException(
                        "Paddle returned an incomplete checkout response"
                );
            }

            return new CreatedTransaction(
                    response.data().id(),
                    response.data().checkout().url()
            );
        } catch (RestClientResponseException exception) {
            log.error(
                    "Paddle rejected transaction creation: status={}",
                    exception.getStatusCode()
            );

            throw new PaymentProviderException(
                    "The payment provider could not create the checkout",
                    exception
            );
        } catch (RestClientException exception) {
            log.error(
                    "Paddle transaction request failed",
                    exception
            );

            throw new PaymentProviderException(
                    "The payment provider is temporarily unavailable",
                    exception
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CreateTransactionRequest(
            List<TransactionItem> items,

            @JsonProperty("collection_mode")
            String collectionMode,

            @JsonProperty("custom_data")
            Map<String, String> customData,

            CheckoutRequest checkout
    ) {
    }

    private record TransactionItem(
            @JsonProperty("price_id")
            String priceId,

            int quantity
    ) {
    }

    private record CheckoutRequest(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateTransactionResponse(
            TransactionData data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TransactionData(
            String id,
            CheckoutData checkout
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckoutData(String url) {
    }

    public record CreatedTransaction(
            String transactionId,
            String checkoutUrl
    ) {
    }

    /** One page of events, newest first. {@code after} is the last event ID of the previous page. */
    public JsonNode listEvents(String after, int perPage) {
        try {
            JsonNode body = (after == null || after.isBlank())
                    ? restClient.get()
                    .uri("/events?order_by={o}&per_page={p}", "id[DESC]", perPage)
                    .retrieve().body(JsonNode.class)
                    : restClient.get()
                    .uri("/events?order_by={o}&per_page={p}&after={a}",
                            "id[DESC]", perPage, after)
                    .retrieve().body(JsonNode.class);

            if (body == null || !body.path("data").isArray()) {
                throw new PaymentProviderException("Paddle returned an invalid events response");
            }
            return body;
        } catch (RestClientResponseException exception) {
            log.error("Paddle rejected events request: status={}", exception.getStatusCode());
            throw new PaymentProviderException(
                    "The payment provider could not list events", exception);
        } catch (RestClientException exception) {
            log.error("Paddle events request failed", exception);
            throw new PaymentProviderException(
                    "The payment provider is temporarily unavailable", exception);
        }
    }
}