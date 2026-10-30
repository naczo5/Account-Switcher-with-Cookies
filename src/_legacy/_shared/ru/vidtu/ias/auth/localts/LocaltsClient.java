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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.utils.GSONUtils;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Client for the Localts marketplace REST API ({@code https://localts.store/v1}).
 * <p>
 * Supports querying user profile/balance, browsing products, purchasing items with credits,
 * querying order history, retrieving packaged account tokens/cookies, and submitting replacements.
 *
 * @author VidTu
 */
public final class LocaltsClient {
    /**
     * Default base URL for Localts API v1.
     */
    public static final String DEFAULT_BASE_URL = "https://localts.store/v1";

    /**
     * Standard request timeout.
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(15);

    /**
     * Browser-like User-Agent to avoid generic automated client blocks.
     */
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 CookieIAS/3.4.0";

    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/LocaltsClient");

    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @NotNull
    private final String apiKey;

    @NotNull
    private final String baseUrl;

    @NotNull
    private final HttpClient client;

    /**
     * Creates a new Localts client with the default base URL and shared HTTP client.
     *
     * @param apiKey User's API key from {@code https://localts.store/user/settings#api-keys}
     */
    public LocaltsClient(@NotNull String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_CLIENT);
    }

    /**
     * Creates a new Localts client with a custom base URL and shared HTTP client.
     *
     * @param apiKey  User's API key
     * @param baseUrl API base URL (must include {@code /v1} prefix or trailing path)
     */
    public LocaltsClient(@NotNull String apiKey, @NotNull String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_CLIENT);
    }

    /**
     * Creates a new Localts client with full parameters.
     *
     * @param apiKey  User's API key
     * @param baseUrl API base URL
     * @param client  Underlying HTTP client
     */
    public LocaltsClient(@NotNull String apiKey, @NotNull String baseUrl, @NotNull HttpClient client) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey").strip();
        String normalized = Objects.requireNonNull(baseUrl, "baseUrl").strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
        this.client = Objects.requireNonNull(client, "client");
    }

    @NotNull
    public String apiKey() {
        return this.apiKey;
    }

    @NotNull
    public String baseUrl() {
        return this.baseUrl;
    }

    /**
     * Fetches current authenticated user information and credit balance.
     *
     * @return User info and credit balance
     * @throws FriendlyException On authentication failure, rate limiting, or API error
     */
    @CheckReturnValue
    @NotNull
    public LocaltsMe getMe() {
        requireApiKey();
        JsonObject json = sendJson(buildRequest("/me").GET().build(), "GET /v1/me");
        try {
            return parseMe(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts account data: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Lists all available store products. Authentication is optional for this endpoint.
     *
     * @return List of available products
     * @throws FriendlyException On network failure or API error
     */
    @CheckReturnValue
    @NotNull
    public List<LocaltsProduct> getProducts() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + "/products"))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(TIMEOUT);
        if (!this.apiKey.isBlank()) {
            builder.header("X-API-Key", this.apiKey);
        }
        JsonObject json = sendJson(builder.GET().build(), "GET /v1/products");
        try {
            return parseProducts(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts products: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Purchases a product by ID, deducting credits from the authenticated user's balance.
     *
     * @param productId ID of the product to purchase
     * @param amount    Number of items to purchase (must be >= 1)
     * @return Purchase result containing the new order ID
     * @throws FriendlyException If insufficient balance, out of stock, unauthorized, or invalid product
     */
    @CheckReturnValue
    @NotNull
    public LocaltsPurchaseResult purchase(@NotNull String productId, int amount) {
        requireApiKey();
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be at least 1: " + amount);
        }
        String path = "/products/" + urlEncode(productId) + "/purchase?amount=" + amount;
        JsonObject json = sendJson(buildRequest(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), "POST /v1/products/.../purchase");
        try {
            return parsePurchaseResult(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts purchase result: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Lists paginated order history for the authenticated user.
     *
     * @param page Zero-based page index
     * @param size Number of orders per page (1 to 100)
     * @return Paginated orders page
     * @throws FriendlyException On authentication failure or bad parameters
     */
    @CheckReturnValue
    @NotNull
    public LocaltsOrdersPage getOrders(int page, int size) {
        requireApiKey();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        String path = "/orders?page=" + safePage + "&size=" + safeSize;
        JsonObject json = sendJson(buildRequest(path).GET().build(), "GET /v1/orders");
        try {
            return parseOrdersPage(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts orders: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Retrieves full order details including packaged items (tokens/cookies).
     *
     * @param orderId The order ID to fetch
     * @return Full order details
     * @throws FriendlyException If unauthorized, order not found, or not owned by user
     */
    @CheckReturnValue
    @NotNull
    public LocaltsOrder getOrder(@NotNull String orderId) {
        requireApiKey();
        String path = "/orders/get-order?id=" + urlEncode(orderId.strip());
        JsonObject json = sendJson(buildRequest(path).GET().build(), "GET /v1/orders/get-order");
        try {
            return parseOrder(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts order details: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
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
     * @throws FriendlyException On validation failure, unauthorized, or rejection
     */
    @CheckReturnValue
    @NotNull
    public LocaltsReplacementResult requestReplacement(@NotNull String orderId, @NotNull String details, int amount,
                                                       @Nullable String issue, @Nullable List<Path> files) {
        requireApiKey();
        String path = "/orders/" + urlEncode(orderId.strip()) + "/replace";
        String boundary = "----CookieIASBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, details, amount, issue, files != null ? files : List.of());

        HttpRequest request = buildRequest(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        JsonObject json = sendJson(request, "POST /v1/orders/.../replace");
        try {
            return parseReplacementResult(json);
        } catch (Throwable t) {
            throw new FriendlyException("Failed to parse Localts replacement result: " + t.getMessage(), t, "ias.error.localts.failed", t.getMessage());
        }
    }

    /**
     * Extracts all raw account content strings (refresh tokens or cookies) from packaged items in an order.
     *
     * @param order Order to extract items from
     * @return List of content strings ready to be passed to {@code TokenImporter} or {@code CookieParser}
     */
    @CheckReturnValue
    @NotNull
    public static List<String> extractOrderAccountContents(@NotNull LocaltsOrder order) {
        if (!order.hasPackagedItems()) {
            return List.of();
        }
        List<String> contents = new ArrayList<>();
        for (LocaltsOrderItem item : order.items()) {
            String content = item.content().strip();
            if (!content.isBlank()) {
                contents.add(content);
            }
        }
        return Collections.unmodifiableList(contents);
    }

    private void requireApiKey() {
        if (this.apiKey.isBlank()) {
            throw new FriendlyException("Localts API key is not configured.", "ias.error.localts.missingKey");
        }
    }

    @NotNull
    private HttpRequest.Builder buildRequest(@NotNull String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + path))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("X-API-Key", this.apiKey)
                .timeout(TIMEOUT);
    }

    @NotNull
    private JsonObject sendJson(@NotNull HttpRequest request, @NotNull String operation) {
        try {
            LOGGER.debug("IAS/Localts: Sending {}", operation);
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            String body = response.body();

            if (code == 401) {
                throw new FriendlyException("Invalid or missing Localts API key (HTTP 401).", "ias.error.localts.unauthorized");
            }
            if (code == 403) {
                throw new FriendlyException("Access forbidden to Localts resource (HTTP 403).", "ias.error.localts.forbidden");
            }
            if (code == 404) {
                throw new FriendlyException("Localts resource not found (HTTP 404).", "ias.error.localts.notFound");
            }
            if (code == 429) {
                throw new FriendlyException("Rate limited by Localts (HTTP 429).", "ias.error.rateLimited");
            }
            if (code < 200 || code >= 300) {
                throw new FriendlyException("Localts server returned HTTP " + code, "ias.error.localts.http", code);
            }

            if (body == null || body.isBlank()) {
                throw new JsonParseException("Received empty response body from " + operation);
            }
            JsonElement parsed = GSONUtils.GSON.fromJson(body, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new JsonParseException("Expected JSON object from " + operation + ", got: " + body);
            }
            JsonObject obj = parsed.getAsJsonObject();
            if (obj.has("success")) {
                JsonElement successElem = obj.get("success");
                if (successElem != null && !successElem.isJsonNull() && successElem.isJsonPrimitive() && !successElem.getAsBoolean()) {
                    String error = optString(obj, "error", "Operation failed");
                    throw new FriendlyException(error, "ias.error.localts.failed", error);
                }
            }
            return obj;
        } catch (FriendlyException e) {
            throw e;
        } catch (Throwable t) {
            throw new FriendlyException("Localts request failed: " + t.getMessage(), t, "ias.error.localts.network", t.getMessage());
        }
    }

    @NotNull
    public static String optString(@Nullable JsonObject obj, @NotNull String key, @NotNull String def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull() && elem.isJsonPrimitive()) ? elem.getAsString() : def;
    }

    public static long optLong(@Nullable JsonObject obj, @NotNull String key, long def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsLong();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int optInt(@Nullable JsonObject obj, @NotNull String key, int def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsInt();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean optBoolean(@Nullable JsonObject obj, @NotNull String key, boolean def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return def;
        try {
            return elem.getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    @Nullable
    public static Integer optNullableInt(@Nullable JsonObject obj, @NotNull String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return null;
        try {
            return elem.getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static String optNullableString(@Nullable JsonObject obj, @NotNull String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) return null;
        return elem.getAsString();
    }

    @CheckReturnValue
    @NotNull
    public static LocaltsMe parseMe(@NotNull JsonObject json) {
        boolean success = optBoolean(json, "success", true);
        String username = optString(json, "username", "");
        long balance = optLong(json, "balance", 0L);
        return new LocaltsMe(success, username, balance);
    }

    @CheckReturnValue
    @NotNull
    public static List<LocaltsProduct> parseProducts(@NotNull JsonObject json) {
        JsonArray array = json.has("products") && json.get("products").isJsonArray()
                ? json.getAsJsonArray("products")
                : new JsonArray();
        List<LocaltsProduct> list = new ArrayList<>(array.size());
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject p = elem.getAsJsonObject();
            String id = optString(p, "id", "");
            String name = optString(p, "name", "");
            String description = optString(p, "description", "");
            String category = optString(p, "category", "");
            int price = optInt(p, "priceInCredits", 0);
            int stock = optInt(p, "stock", 0);
            String type = optString(p, "type", "text");
            String logoUrl = optNullableString(p, "logoUrl");

            List<String> tags = new ArrayList<>();
            if (p.has("tags") && p.get("tags").isJsonArray()) {
                for (JsonElement t : p.getAsJsonArray("tags")) {
                    if (t != null && !t.isJsonNull() && t.isJsonPrimitive()) {
                        tags.add(t.getAsString());
                    }
                }
            }

            Map<String, Integer> discounts = new LinkedHashMap<>();
            if (p.has("quantityDiscounts") && p.get("quantityDiscounts").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : p.getAsJsonObject("quantityDiscounts").entrySet()) {
                    try {
                        if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                            discounts.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            list.add(new LocaltsProduct(id, name, description, category, price, stock, type, logoUrl,
                    Collections.unmodifiableList(tags), Collections.unmodifiableMap(discounts)));
        }
        return Collections.unmodifiableList(list);
    }

    @CheckReturnValue
    @NotNull
    public static LocaltsPurchaseResult parsePurchaseResult(@NotNull JsonObject json) {
        boolean success = optBoolean(json, "success", true);
        String orderId = optString(json, "orderId", optString(json, "order-id", ""));
        return new LocaltsPurchaseResult(success, orderId);
    }

    @CheckReturnValue
    @NotNull
    public static LocaltsOrdersPage parseOrdersPage(@NotNull JsonObject json) {
        boolean success = optBoolean(json, "success", true);
        JsonArray array = json.has("orders") && json.get("orders").isJsonArray()
                ? json.getAsJsonArray("orders")
                : new JsonArray();
        List<LocaltsOrderSummary> list = new ArrayList<>(array.size());
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject o = elem.getAsJsonObject();
            String id = optString(o, "id", "");
            String productId = optString(o, "productId", "");
            String productType = optString(o, "productType", "Text");
            long timestamp = optLong(o, "timestamp", 0L);
            list.add(new LocaltsOrderSummary(id, productId, productType, timestamp));
        }
        int page = optInt(json, "page", 0);
        int size = optInt(json, "size", list.size());
        int totalPages = optInt(json, "totalPages", 1);
        int totalElements = optInt(json, "totalElements", list.size());
        return new LocaltsOrdersPage(success, Collections.unmodifiableList(list), page, size, totalPages, totalElements);
    }

    @CheckReturnValue
    @NotNull
    public static LocaltsOrder parseOrder(@NotNull JsonObject json) {
        boolean success = optBoolean(json, "success", true);
        String orderId = optString(json, "order-id", optString(json, "orderId", ""));
        String status = optString(json, "status", "PENDING");
        String orderStatus = optString(json, "order-status", optString(json, "orderStatus", status));
        Integer refundedCredits = optNullableInt(json, "refunded-credits");
        if (refundedCredits == null) {
            refundedCredits = optNullableInt(json, "refundedCredits");
        }
        Integer refundedItems = optNullableInt(json, "refunded-items");
        if (refundedItems == null) {
            refundedItems = optNullableInt(json, "refundedItems");
        }
        String productName = optNullableString(json, "product-name");
        if (productName == null) {
            productName = optNullableString(json, "productName");
        }

        LocaltsOrderReplacement replacement = null;
        if (json.has("replacement") && json.get("replacement").isJsonObject()) {
            JsonObject r = json.getAsJsonObject("replacement");
            String rId = optString(r, "id", "");
            String rStatus = optString(r, "status", "OPEN");
            int rItems = optInt(r, "items", 1);
            replacement = new LocaltsOrderReplacement(rId, rStatus, rItems);
        }

        List<LocaltsOrderItem> items = new ArrayList<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("items")) {
                if (!elem.isJsonObject()) continue;
                JsonObject item = elem.getAsJsonObject();
                String itemId = optString(item, "id", "");
                String content = optString(item, "content", "");
                items.add(new LocaltsOrderItem(itemId, content));
            }
        }

        return new LocaltsOrder(success, orderId, status, orderStatus, refundedCredits, refundedItems,
                replacement, productName, Collections.unmodifiableList(items));
    }

    @CheckReturnValue
    @NotNull
    public static LocaltsReplacementResult parseReplacementResult(@NotNull JsonObject json) {
        boolean success = optBoolean(json, "success", true);
        String replacementId = optString(json, "replacementId", optString(json, "replacement-id", ""));
        String redirect = optNullableString(json, "redirect");
        return new LocaltsReplacementResult(success, replacementId, redirect);
    }

    private static byte[] buildMultipartBody(String boundary, String details, int amount,
                                            @Nullable String issue, List<Path> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeFormField(out, boundary, "details", details);
            writeFormField(out, boundary, "amount", Integer.toString(amount));
            if (issue != null && !issue.isBlank()) {
                writeFormField(out, boundary, "issue", issue);
            }
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    String filename = file.getFileName().toString();
                    String mimeType = Files.probeContentType(file);
                    if (mimeType == null || mimeType.isBlank()) {
                        mimeType = "application/octet-stream";
                    }
                    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    Files.copy(file, out);
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to construct multipart body", e);
        }
        return out.toByteArray();
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
