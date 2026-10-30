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

package ru.vidtu.ias.auth.localts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Client for the Localts marketplace REST API ({@code https://localts.store/v1}).
 * <p>
 * Supports querying user profile/balance, browsing products, purchasing items with credits,
 * querying order history, retrieving packaged account tokens/cookies, and submitting replacements.
 * Built with standard Java 8 HttpURLConnection for compatibility with both modern Fabric and Forge 1.8.9.
 *
 * @author VidTu
 */
public final class LocaltsClient {
    /**
     * Default base URL for Localts API v1.
     */
    public static final String DEFAULT_BASE_URL = "https://localts.store/v1";

    /**
     * Standard request timeout in milliseconds.
     */
    public static final int TIMEOUT_MS = 15000;

    /**
     * Browser-like User-Agent to avoid generic automated client blocks.
     */
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 CookieIAS/3.4.1";

    private final String apiKey;
    private final String baseUrl;

    /**
     * Creates a new Localts client with the default base URL.
     *
     * @param apiKey User's API key from {@code https://localts.store/user/settings#api-keys}
     */
    public LocaltsClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    /**
     * Creates a new Localts client with a custom base URL.
     *
     * @param apiKey  User's API key
     * @param baseUrl API base URL (must include {@code /v1} prefix or trailing path)
     */
    public LocaltsClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        String normalized = baseUrl != null ? baseUrl.trim() : DEFAULT_BASE_URL;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    public String apiKey() {
        return this.apiKey;
    }

    public String baseUrl() {
        return this.baseUrl;
    }

    /**
     * Verifies that an API key has been supplied.
     *
     * @throws LocaltsException If API key is blank or missing
     */
    public void requireApiKey() {
        if (this.apiKey.isEmpty()) {
            throw new LocaltsException("Localts API key is required for this action.", "ias.error.localts.missingKey");
        }
    }

    /**
     * Retrieves username and current credit balance for the authenticated user.
     *
     * @return User account info and balance
     * @throws LocaltsException On 401 unauthorized or API failure
     */
    public LocaltsMe getMe() {
        requireApiKey();
        JsonObject json = sendJson("GET", this.baseUrl + "/me", null, null, "GET /v1/me");
        try {
            return parseMe(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts profile: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Retrieves a list of available public store products.
     *
     * @return List of available products
     * @throws LocaltsException On network failure or API error
     */
    public List<LocaltsProduct> getProducts() {
        JsonObject json = sendJson("GET", this.baseUrl + "/products", null, null, "GET /v1/products");
        try {
            return parseProducts(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts products: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Purchases a product by ID, deducting credits from the authenticated user's balance.
     *
     * @param productId ID of the product to purchase
     * @param amount    Number of items to purchase (must be >= 1)
     * @return Purchase result containing the new order ID
     * @throws LocaltsException If insufficient balance, out of stock, unauthorized, or invalid product
     */
    public LocaltsPurchaseResult purchase(String productId, int amount) {
        requireApiKey();
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be at least 1: " + amount);
        }
        String path = this.baseUrl + "/products/" + urlEncode(productId) + "/purchase?amount=" + amount;
        JsonObject json = sendJson("POST", path, null, null, "POST /v1/products/.../purchase");
        try {
            return parsePurchaseResult(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts purchase result: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Lists paginated order history for the authenticated user.
     *
     * @param page Zero-based page index
     * @param size Number of orders per page (1 to 100)
     * @return Paginated orders page
     * @throws LocaltsException On authentication failure or bad parameters
     */
    public LocaltsOrdersPage getOrders(int page, int size) {
        requireApiKey();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        String url = this.baseUrl + "/orders?page=" + safePage + "&size=" + safeSize;
        JsonObject json = sendJson("GET", url, null, null, "GET /v1/orders");
        try {
            return parseOrdersPage(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts orders: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Retrieves full order details including packaged items (tokens/cookies).
     *
     * @param orderId The order ID to fetch
     * @return Full order details
     * @throws LocaltsException If unauthorized, order not found, or not owned by user
     */
    public LocaltsOrder getOrder(String orderId) {
        requireApiKey();
        String url = this.baseUrl + "/orders/get-order?id=" + urlEncode(orderId != null ? orderId.trim() : "");
        JsonObject json = sendJson("GET", url, null, null, "GET /v1/orders/get-order");
        try {
            return parseOrder(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts order details: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Submits a replacement request for an order under warranty.
     *
     * @param orderId The order ID
     * @param details Description of the problem (up to 1000 characters)
     * @param amount  Number of items to replace
     * @param issue   Optional problem label (up to 100 characters)
     * @param files   Optional proof files (screenshots/videos up to 10 files, 20MB each)
     * @return Replacement result
     * @throws LocaltsException On validation failure, unauthorized, or rejection
     */
    public LocaltsReplacementResult requestReplacement(String orderId, String details, int amount,
                                                       String issue, List<Path> files) {
        requireApiKey();
        String url = this.baseUrl + "/orders/" + urlEncode(orderId != null ? orderId.trim() : "") + "/replace";
        String boundary = "----CookieIASBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, details, amount, issue, files != null ? files : Collections.<Path>emptyList());
        JsonObject json = sendJson("POST", url, body, "multipart/form-data; boundary=" + boundary, "POST /v1/orders/.../replace");
        try {
            return parseReplacementResult(json);
        } catch (Throwable t) {
            throw new LocaltsException("Failed to parse Localts replacement result: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Extracts raw account tokens or cookie payloads from a packaged order.
     *
     * @param order A packaged Localts order
     * @return Non-empty list of raw account payloads ready for CookieParser
     */
    public static List<String> extractOrderAccounts(LocaltsOrder order) {
        return extractOrderAccountContents(order);
    }

    /**
     * Extracts raw account tokens or cookie payloads from a packaged order.
     *
     * @param order A packaged Localts order
     * @return Non-empty list of raw account payloads ready for CookieParser
     */
    public static List<String> extractOrderAccountContents(LocaltsOrder order) {
        if (order == null || !order.hasPackagedItems()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<String>();
        for (LocaltsOrderItem item : order.items()) {
            String c = item.content().trim();
            if (!c.isEmpty()) {
                list.add(c);
            }
        }
        return list;
    }

    private JsonObject sendJson(String method, String urlStr, byte[] body, String contentType, String operation) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");

            if (!this.apiKey.isEmpty()) {
                conn.setRequestProperty("X-API-Key", this.apiKey);
            }

            if (body != null) {
                conn.setDoOutput(true);
                if (contentType != null) {
                    conn.setRequestProperty("Content-Type", contentType);
                }
                conn.setFixedLengthStreamingMode(body.length);
                try {
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body);
                        os.flush();
                    }
                } catch (IOException e) {
                    // If the server rejected early (e.g. 401/403/429), check response code
                    try {
                        int earlyCode = conn.getResponseCode();
                        if (earlyCode >= 400) {
                            handleHttpError(conn, earlyCode);
                        }
                    } catch (LocaltsException le) {
                        throw le;
                    } catch (Throwable ignored) {
                    }
                    throw e;
                }
            }

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                handleHttpError(conn, code);
            }

            String respBody = readStream(conn.getInputStream());
            if (respBody.trim().isEmpty()) {
                throw new JsonParseException("Received empty response body from " + operation);
            }
            JsonElement parsed = new JsonParser().parse(respBody);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Expected JSON object from " + operation + ", got: " + respBody);
            }
            JsonObject obj = parsed.getAsJsonObject();
            if (obj.has("success")) {
                JsonElement successElem = obj.get("success");
                if (successElem != null && !successElem.isJsonNull() && successElem.isJsonPrimitive() && !successElem.getAsBoolean()) {
                    String error = optString(obj, "error", "Operation failed");
                    if (isOutOfStock(error)) {
                        throw new LocaltsException(error, "ias.localts.buy.outOfStock");
                    }
                    throw new LocaltsException(error, "ias.error.localts.failed", error);
                }
            }
            return obj;
        } catch (LocaltsException e) {
            if (conn != null) conn.disconnect();
            throw e;
        } catch (Throwable t) {
            if (conn != null) conn.disconnect();
            throw new LocaltsException("Localts request failed: " + t.getMessage(), t, "ias.error.localts.network", t.getMessage());
        }
    }

    private static void handleHttpError(HttpURLConnection conn, int code) throws IOException {
        if (code == 401) {
            throw new LocaltsException("Invalid or missing Localts API key (HTTP 401).", "ias.error.localts.unauthorized");
        }
        if (code == 403) {
            throw new LocaltsException("Access forbidden to Localts resource (HTTP 403).", "ias.error.localts.forbidden");
        }
        if (code == 404) {
            throw new LocaltsException("Localts resource not found (HTTP 404).", "ias.error.localts.notFound");
        }
        if (code == 429) {
            throw new LocaltsException("Rate limited by Localts (HTTP 429).", "ias.error.rateLimited");
        }
        String errText = readStream(conn.getErrorStream());
        String extractedMsg = null;
        try {
            JsonElement elem = new JsonParser().parse(errText);
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                extractedMsg = optString(obj, "error", optString(obj, "message", null));
            }
        } catch (Throwable ignored) {
        }
        if (extractedMsg != null) {
            if (isOutOfStock(extractedMsg)) {
                throw new LocaltsException(extractedMsg, "ias.localts.buy.outOfStock");
            }
            throw new LocaltsException(extractedMsg, "ias.error.localts.failed", extractedMsg);
        }
        throw new LocaltsException("Localts server returned HTTP " + code, "ias.error.localts.http", code);
    }

    /**
     * Detects the common out-of-stock error wording used by the store API.
     *
     * @param message Server error message
     * @return Whether the message describes an out-of-stock condition
     */
    private static boolean isOutOfStock(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("out of stock")
                || lower.contains("sold out")
                || lower.contains("no stock")
                || lower.contains("not enough stock")
                || lower.contains("insufficient stock");
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            is.close();
        }
    }

    public static String optString(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull() && elem.isJsonPrimitive()) ? elem.getAsString() : def;
    }

    public static long optLong(JsonObject obj, String key, long def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsLong();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int optInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsInt();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean optBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    public static Integer optNullableInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return null;
        try {
            return elem.getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String optNullableString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull() && elem.isJsonPrimitive()) ? elem.getAsString() : null;
    }

    public static LocaltsMe parseMe(JsonObject obj) {
        boolean success = optBoolean(obj, "success", true);
        String username = optString(obj, "username", "");
        long balance = optLong(obj, "balance", 0L);
        return new LocaltsMe(success, username, balance);
    }

    public static List<LocaltsProduct> parseProducts(JsonObject obj) {
        List<LocaltsProduct> result = new ArrayList<LocaltsProduct>();
        if (obj == null) return result;
        JsonArray array = null;
        if (obj.has("products") && obj.get("products").isJsonArray()) {
            array = obj.getAsJsonArray("products");
        } else if (obj.has("data") && obj.get("data").isJsonArray()) {
            array = obj.getAsJsonArray("data");
        }
        if (array == null) return result;

        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject p = elem.getAsJsonObject();
            String id = optString(p, "id", "");
            String name = optString(p, "name", "");
            String description = optString(p, "description", "");
            String category = optString(p, "category", "");
            int price = optInt(p, "priceInCredits", 0);
            int stock = optInt(p, "stock", -1);
            String type = optString(p, "type", "text");
            String logoUrl = optNullableString(p, "logoUrl");

            List<String> tags = new ArrayList<String>();
            if (p.has("tags") && p.get("tags").isJsonArray()) {
                for (JsonElement t : p.getAsJsonArray("tags")) {
                    if (t.isJsonPrimitive()) tags.add(t.getAsString());
                }
            }

            Map<String, Integer> discounts = new LinkedHashMap<String, Integer>();
            if (p.has("quantityDiscounts") && p.get("quantityDiscounts").isJsonObject()) {
                JsonObject qd = p.getAsJsonObject("quantityDiscounts");
                for (Map.Entry<String, JsonElement> e : qd.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        try {
                            discounts.put(e.getKey(), e.getValue().getAsInt());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            result.add(new LocaltsProduct(id, name, description, category, price, stock, type, logoUrl, tags, discounts));
        }
        return result;
    }

    public static LocaltsPurchaseResult parsePurchaseResult(JsonObject obj) {
        boolean success = optBoolean(obj, "success", true);
        String orderId = optString(obj, "orderId", optString(obj, "order-id", ""));
        return new LocaltsPurchaseResult(success, orderId);
    }

    public static LocaltsOrdersPage parseOrdersPage(JsonObject obj) {
        boolean success = optBoolean(obj, "success", true);
        int page = optInt(obj, "page", 0);
        int size = optInt(obj, "size", 0);
        int totalPages = optInt(obj, "totalPages", 0);
        int totalElements = optInt(obj, "totalElements", 0);
        List<LocaltsOrderSummary> list = new ArrayList<LocaltsOrderSummary>();

        if (obj.has("orders") && obj.get("orders").isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray("orders")) {
                if (!elem.isJsonObject()) continue;
                JsonObject o = elem.getAsJsonObject();
                String id = optString(o, "id", "");
                String productId = optString(o, "productId", "");
                String productType = optString(o, "productType", "Text");
                long timestamp = optLong(o, "timestamp", 0L);
                list.add(new LocaltsOrderSummary(id, productId, productType, timestamp));
            }
        }
        return new LocaltsOrdersPage(success, list, page, size, totalPages, totalElements);
    }

    public static LocaltsOrder parseOrder(JsonObject obj) {
        boolean success = optBoolean(obj, "success", true);
        String orderId = optString(obj, "order-id", optString(obj, "orderId", ""));
        String status = optString(obj, "status", "PENDING");
        String orderStatus = optString(obj, "order-status", optString(obj, "orderStatus", status));
        Integer refundedCredits = optNullableInt(obj, "refunded-credits");
        if (refundedCredits == null) {
            refundedCredits = optNullableInt(obj, "refundedCredits");
        }
        Integer refundedItems = optNullableInt(obj, "refunded-items");
        if (refundedItems == null) {
            refundedItems = optNullableInt(obj, "refundedItems");
        }
        String productName = optNullableString(obj, "product-name");
        if (productName == null) {
            productName = optNullableString(obj, "productName");
        }

        LocaltsOrderReplacement replacement = null;
        if (obj.has("replacement") && obj.get("replacement").isJsonObject()) {
            JsonObject r = obj.getAsJsonObject("replacement");
            replacement = new LocaltsOrderReplacement(
                    optString(r, "id", ""),
                    optString(r, "status", "OPEN"),
                    optInt(r, "items", 0)
            );
        }

        List<LocaltsOrderItem> items = new ArrayList<LocaltsOrderItem>();
        if (obj.has("items") && obj.get("items").isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray("items")) {
                if (!elem.isJsonObject()) continue;
                JsonObject it = elem.getAsJsonObject();
                items.add(new LocaltsOrderItem(optString(it, "id", ""), optString(it, "content", "")));
            }
        }
        return new LocaltsOrder(success, orderId, status, orderStatus, refundedCredits, refundedItems, replacement, productName, items);
    }

    public static LocaltsReplacementResult parseReplacementResult(JsonObject obj) {
        boolean success = optBoolean(obj, "success", true);
        String replacementId = optString(obj, "replacementId", optString(obj, "replacement-id", ""));
        String redirect = optNullableString(obj, "redirect");
        return new LocaltsReplacementResult(success, replacementId, redirect);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Throwable t) {
            return s != null ? s : "";
        }
    }

    private static byte[] buildMultipartBody(String boundary, String details, int amount,
                                             String issue, List<Path> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            appendField(out, boundary, "details", details);
            appendField(out, boundary, "amount", String.valueOf(amount));
            if (issue != null && !issue.trim().isEmpty()) {
                appendField(out, boundary, "issue", issue.trim());
            }
            for (Path path : files) {
                if (path == null || !Files.isRegularFile(path)) continue;
                String filename = path.getFileName().toString();
                String mime = Files.probeContentType(path);
                if (mime == null) mime = "application/octet-stream";
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(Files.readAllBytes(path));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new LocaltsException("Failed to construct multipart replacement request: " + e.getMessage(), e, "ias.error.localts.network", e.getMessage());
        }
    }

    private static void appendField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
