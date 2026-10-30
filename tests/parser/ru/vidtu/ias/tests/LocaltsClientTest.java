/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias.tests;

import com.google.gson.JsonObject;
import ru.vidtu.ias.auth.localts.LocaltsClient;
import ru.vidtu.ias.auth.localts.LocaltsMe;
import ru.vidtu.ias.auth.localts.LocaltsOrder;
import ru.vidtu.ias.auth.localts.LocaltsOrdersPage;
import ru.vidtu.ias.auth.localts.LocaltsProduct;
import ru.vidtu.ias.auth.localts.LocaltsPurchaseResult;
import ru.vidtu.ias.auth.localts.LocaltsReplacementResult;
import ru.vidtu.ias.utils.GSONUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone unit tests for Localts API client and payload parsing.
 */
public final class LocaltsClientTest {
    private LocaltsClientTest() {
        throw new AssertionError("No instances.");
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();
        run("parseMe", LocaltsClientTest::testParseMe, failures);
        run("parseProducts", LocaltsClientTest::testParseProducts, failures);
        run("parsePurchaseResult", LocaltsClientTest::testParsePurchaseResult, failures);
        run("parseOrdersPage", LocaltsClientTest::testParseOrdersPage, failures);
        run("parsePackagedOrder", LocaltsClientTest::testParsePackagedOrder, failures);
        run("parsePendingOrder", LocaltsClientTest::testParsePendingOrder, failures);
        run("parseRefundedOrder", LocaltsClientTest::testParseRefundedOrder, failures);
        run("fullyRefundedOrderIgnored", LocaltsClientTest::testFullyRefundedOrderIgnored, failures);
        run("parseReplacementResult", LocaltsClientTest::testParseReplacementResult, failures);
        run("extractOrderAccounts", LocaltsClientTest::testExtractOrderAccounts, failures);
        run("malformedAndNullSafe", LocaltsClientTest::testMalformedAndNullSafe, failures);

        int passed = 11 - failures.size();
        System.out.println(passed + "/11 LocaltsClient tests passed.");
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                System.err.println("FAIL: " + failure);
            }
            System.exit(1);
        }
    }

    private static void run(String name, TestCase test, List<String> failures) {
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            System.out.println("FAIL " + name + ": " + t.getMessage());
            failures.add(name + ": " + t.getMessage());
        }
    }

    private static void testParseMe() {
        String jsonStr = """
                {
                  "success": true,
                  "username": "exampleuser",
                  "balance": 1250
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsMe me = LocaltsClient.parseMe(json);
        assertTrue(me.success(), "me.success should be true");
        assertEquals("exampleuser", me.username(), "username mismatch");
        assertEquals(1250L, me.balance(), "balance mismatch");
    }

    private static void testParseProducts() {
        String jsonStr = """
                {
                  "success": true,
                  "products": [
                    {
                      "id": "prod_1",
                      "name": "Minecraft Refresh Alt",
                      "description": "Full access refresh token",
                      "category": "Minecraft",
                      "priceInCredits": 150,
                      "stock": 42,
                      "type": "text",
                      "logoUrl": "/api/product/prod_1/gallery/logo",
                      "tags": ["new", "popular"],
                      "quantityDiscounts": {
                        "5": 10,
                        "10": 20
                      }
                    }
                  ]
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        List<LocaltsProduct> products = LocaltsClient.parseProducts(json);
        assertEquals(1, products.size(), "product count mismatch");
        LocaltsProduct p = products.get(0);
        assertEquals("prod_1", p.id(), "id mismatch");
        assertEquals("Minecraft Refresh Alt", p.name(), "name mismatch");
        assertEquals(150, p.priceInCredits(), "price mismatch");
        assertEquals(42, p.stock(), "stock mismatch");
        assertEquals(2, p.tags().size(), "tags count mismatch");
        assertEquals(Integer.valueOf(10), p.quantityDiscounts().get("5"), "discount mismatch");
        assertEquals(Integer.valueOf(20), p.quantityDiscounts().get("10"), "discount mismatch");
    }

    private static void testParsePurchaseResult() {
        String jsonStr = """
                {
                  "success": true,
                  "orderId": "ord_999"
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsPurchaseResult res = LocaltsClient.parsePurchaseResult(json);
        assertTrue(res.success(), "purchase success");
        assertEquals("ord_999", res.orderId(), "order id mismatch");

        String kebabJsonStr = """
                {
                  "success": true,
                  "order-id": "ord_kebab_123"
                }
                """;
        JsonObject kebabJson = GSONUtils.GSON.fromJson(kebabJsonStr, JsonObject.class);
        LocaltsPurchaseResult kebabRes = LocaltsClient.parsePurchaseResult(kebabJson);
        assertTrue(kebabRes.success(), "kebab purchase success");
        assertEquals("ord_kebab_123", kebabRes.orderId(), "kebab order id mismatch");
    }

    private static void testParseOrdersPage() {
        String jsonStr = """
                {
                  "success": true,
                  "orders": [
                    {
                      "id": "ord_1",
                      "productId": "prod_1",
                      "productType": "Text",
                      "timestamp": 1711720800000
                    }
                  ],
                  "page": 0,
                  "size": 25,
                  "totalPages": 2,
                  "totalElements": 35
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrdersPage page = LocaltsClient.parseOrdersPage(json);
        assertTrue(page.success(), "orders page success");
        assertEquals(1, page.orders().size(), "orders size mismatch");
        assertEquals("ord_1", page.orders().get(0).id(), "order 0 id mismatch");
        assertEquals(0, page.page(), "page mismatch");
        assertEquals(25, page.size(), "size mismatch");
        assertEquals(2, page.totalPages(), "totalPages mismatch");
        assertEquals(35, page.totalElements(), "totalElements mismatch");
    }

    private static void testParsePackagedOrder() {
        String jsonStr = """
                {
                  "success": true,
                  "order-id": "ord_123",
                  "status": "PACKAGED",
                  "order-status": "COMPLETE",
                  "product-name": "Minecraft Refresh Alt",
                  "items": [
                    {
                      "id": "item_1",
                      "content": "PlayerOne:M.C512_BAY.0.U.MsaArtifacts.TEST-FAKE-TOKEN-1234567890123456789012345678901234567890"
                    },
                    {
                      "id": "item_2",
                      "content": "PlayerTwo:M.C512_BAY.0.U.MsaArtifacts.TEST-FAKE-TOKEN-0987654321098765432109876543210987654321"
                    }
                  ]
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrder order = LocaltsClient.parseOrder(json);
        assertTrue(order.success(), "order success");
        assertEquals("ord_123", order.orderId(), "order id mismatch");
        assertEquals("PACKAGED", order.status(), "status mismatch");
        assertEquals("COMPLETE", order.orderStatus(), "order-status mismatch");
        assertEquals("Minecraft Refresh Alt", order.productName(), "product name mismatch");
        assertTrue(order.hasPackagedItems(), "order should have packaged items");
        assertEquals(2, order.items().size(), "items count mismatch");
    }

    private static void testParsePendingOrder() {
        String jsonStr = """
                {
                  "success": true,
                  "order-id": "ord_pending",
                  "status": "PENDING",
                  "order-status": "PENDING",
                  "items": []
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrder order = LocaltsClient.parseOrder(json);
        assertTrue(order.success(), "order success");
        assertEquals("PENDING", order.status(), "status mismatch");
        assertTrue(!order.hasPackagedItems(), "order should not have packaged items");
        assertEquals(0, order.items().size(), "items should be empty");
    }

    private static void testParseRefundedOrder() {
        String jsonStr = """
                {
                  "success": true,
                  "order-id": "ord_ref",
                  "status": "PACKAGED",
                  "order-status": "PARTIALLY_REFUNDED",
                  "refunded-credits": 150,
                  "refunded-items": 1,
                  "replacement": {
                    "id": "rep_1",
                    "status": "ACCEPTED",
                    "items": 1
                  },
                  "product-name": "Minecraft Refresh Alt",
                  "items": [
                    {
                      "id": "item_ok",
                      "content": "PlayerGood:M.C512_BAY.0.U.MsaArtifacts.TEST-FAKE-TOKEN-1234567890123456789012345678901234567890"
                    }
                  ]
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrder order = LocaltsClient.parseOrder(json);
        assertEquals(Integer.valueOf(150), order.refundedCredits(), "refunded credits mismatch");
        assertEquals(Integer.valueOf(1), order.refundedItems(), "refunded items mismatch");
        assertTrue(order.replacement() != null, "replacement should not be null");
        assertEquals("rep_1", order.replacement().id(), "replacement id mismatch");
        assertEquals("ACCEPTED", order.replacement().status(), "replacement status mismatch");
        assertEquals(1, order.items().size(), "items count mismatch");
    }

    private static void testParseReplacementResult() {
        String jsonStr = """
                {
                  "success": true,
                  "replacementId": "rep_999",
                  "redirect": "/user/replacements/rep_999"
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsReplacementResult res = LocaltsClient.parseReplacementResult(json);
        assertTrue(res.success(), "replacement success");
        assertEquals("rep_999", res.replacementId(), "replacement id mismatch");
        assertEquals("/user/replacements/rep_999", res.redirect(), "redirect mismatch");
    }

    private static void testExtractOrderAccounts() {
        String jsonStr = """
                {
                  "success": true,
                  "order-id": "ord_extract",
                  "status": "PACKAGED",
                  "order-status": "COMPLETE",
                  "items": [
                    {
                      "id": "item_1",
                      "content": "Thanks for choosing Localts!\\nPlayerOne:M.C512_BAY.0.U.MsaArtifacts.TOKEN1_1234567890123456789012345678901234567890"
                    },
                    {
                      "id": "item_2",
                      "content": "PlayerTwo:M.C512_BAY.0.U.MsaArtifacts.TOKEN2_1234567890123456789012345678901234567890"
                    }
                  ]
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrder order = LocaltsClient.parseOrder(json);
        List<String> contents = LocaltsClient.extractOrderAccountContents(order);
        assertEquals(2, contents.size(), "extracted contents count");

        assertTrue(contents.get(0).contains("M.C512_BAY.0.U.MsaArtifacts.TOKEN1"), "item 0 token format");
        assertTrue(contents.get(1).contains("M.C512_BAY.0.U.MsaArtifacts.TOKEN2"), "item 1 token format");
    }

    private static void testFullyRefundedOrderIgnored() {
        String jsonStr = """
                {
                  "success": true,
                  "order-id": "ord_fully_refunded",
                  "status": "PACKAGED",
                  "order-status": "REFUNDED",
                  "items": [
                    {
                      "id": "item_dead",
                      "content": "PlayerDead:M.C512_BAY.0.U.MsaArtifacts.FAKE-TOKEN"
                    }
                  ]
                }
                """;
        JsonObject json = GSONUtils.GSON.fromJson(jsonStr, JsonObject.class);
        LocaltsOrder order = LocaltsClient.parseOrder(json);
        assertTrue(!order.hasPackagedItems(), "fully REFUNDED order must not have packaged items for import");
    }

    private static void testMalformedAndNullSafe() {
        JsonObject empty = new JsonObject();
        LocaltsMe me = LocaltsClient.parseMe(empty);
        assertTrue(me.success(), "empty me success");
        assertEquals("", me.username(), "empty me username");
        assertEquals(0L, me.balance(), "empty me balance");

        List<LocaltsProduct> products = LocaltsClient.parseProducts(empty);
        assertTrue(products.isEmpty(), "empty products");

        LocaltsOrdersPage page = LocaltsClient.parseOrdersPage(empty);
        assertTrue(page.orders().isEmpty(), "empty orders page");

        LocaltsOrder order = LocaltsClient.parseOrder(empty);
        assertTrue(!order.hasPackagedItems(), "empty order hasPackagedItems");

        LocaltsReplacementResult rep = LocaltsClient.parseReplacementResult(empty);
        assertTrue(rep.success(), "empty replacement");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }
}
