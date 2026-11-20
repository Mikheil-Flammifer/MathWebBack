package com.mathweb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathweb.entity.PaddleCheckoutOrder;
import com.mathweb.entity.Plan;
import com.mathweb.enums.PaddleCheckoutOrderStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaddleTransactionValidator {

    public record Result(
            boolean valid,
            String reason,
            Long actualSubtotalCents,
            Long actualTotalCents
    ) {
        static Result reject(String reason) {
            return new Result(false, reason, null, null);
        }

        static Result ok(long subtotal, long total) {
            return new Result(true, null, subtotal, total);
        }
    }

    public Result validate(JsonNode data,
                           PaddleCheckoutOrder order,
                           Instant occurredAt) {

        if (data == null || !data.isObject()) {
            return Result.reject("Missing data object");
        }

        if (!"completed".equals(text(data, "status"))) {
            return Result.reject("Transaction status is not completed");
        }

        String txnId = text(data, "id");
        if (txnId == null || !txnId.equals(order.getPaddleTransactionId())) {
            return Result.reject("Transaction ID does not match order");
        }

        // Order state
        PaddleCheckoutOrderStatus status = order.getStatus();
        if (order.getCompletedAt() != null
                || status == PaddleCheckoutOrderStatus.COMPLETED) {
            return Result.reject("Order already used");
        }
        if (status != PaddleCheckoutOrderStatus.PENDING
                && status != PaddleCheckoutOrderStatus.READY) {
            return Result.reject("Order is not pending: " + status);
        }
        if (!occurredAt.isBefore(order.getExpiresAt())) {
            return Result.reject("Order expired before payment");
        }

        // Items (request side): exactly one, quantity explicitly 1
        JsonNode items = data.get("items");
        if (items == null || !items.isArray() || items.size() != 1) {
            return Result.reject("Expected exactly one item");
        }

        // Line items (source of truth for money)
        JsonNode details = data.get("details");
        JsonNode lineItems = details == null ? null : details.get("line_items");
        if (lineItems == null || !lineItems.isArray() || lineItems.size() != 1) {
            return Result.reject("Expected exactly one line item");
        }
        JsonNode line = lineItems.get(0);

        if (!isExactlyOne(items.get(0).get("quantity"))
                || !isExactlyOne(line.get("quantity"))) {
            return Result.reject("Quantity must be explicitly 1");
        }

        // Price ID: item, line item, order, plan
        Plan plan = order.getPlan();
        String itemPriceId = text(items.get(0).path("price"), "id");
        String linePriceId = text(line, "price_id");
        if (itemPriceId == null
                || !itemPriceId.equals(linePriceId)
                || !itemPriceId.equals(order.getExpectedPriceId())
                || !itemPriceId.equals(plan.getPaddlePriceId())) {
            return Result.reject("Price ID mismatch");
        }

        // Currency: transaction, order, plan
        String currency = text(data, "currency_code");
        if (currency == null
                || !currency.equalsIgnoreCase(order.getExpectedCurrency())
                || !currency.equalsIgnoreCase(plan.getCurrency())) {
            return Result.reject("Currency mismatch");
        }

        // Amounts (Paddle sends minor units as strings)
        JsonNode lineTotals = line.get("totals");
        JsonNode txnTotals = details.get("totals");
        Long lineSubtotal = amount(lineTotals, "subtotal");
        Long lineDiscount = amount(lineTotals, "discount");
        Long txnDiscount = amount(txnTotals, "discount");
        Long actualTotal = amount(txnTotals, "grand_total");

        if (lineSubtotal == null || lineDiscount == null
                || txnDiscount == null || actualTotal == null) {
            return Result.reject("Missing or invalid totals");
        }

        // Compare subtotal (tax-exclusive) to the expected price, never grand_total
        if (lineSubtotal != order.getExpectedSubtotalCents()
                || lineSubtotal != plan.getPriceCents()) {
            return Result.reject("Subtotal mismatch");
        }

        // Discounts are not supported
        JsonNode discountId = data.get("discount_id");
        if (lineDiscount != 0 || txnDiscount != 0
                || (discountId != null && !discountId.isNull())) {
            return Result.reject("Unexpected discount");
        }

        if (actualTotal < 0 || actualTotal > Integer.MAX_VALUE) {
            return Result.reject("Total out of range");
        }

        return Result.ok(lineSubtotal, actualTotal);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank()
                ? v.asText() : null;
    }

    private static boolean isExactlyOne(JsonNode q) {
        return q != null && q.isIntegralNumber() && q.asLong() == 1L;
    }

    private static Long amount(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}