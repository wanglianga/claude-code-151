package com.citylibrary.pll;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REST 冒烟：真实 HTTP 调用验证路由、JSON 序列化、规则手册与 OpenAPI 文档可用，
 * 以及一条端到端 API 链路（预约 -> 出库 -> 到馆 -> 取书）。
 */
class RestApiSmokeTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_rules_and_openapi_are_reachable() {
        Map<String, Object>[] books = rest.getForObject(
                url("/api/catalog/search?q=三体&pickup=CENTRAL"), Map[].class);
        assertNotNull(books);
        assertEquals(1, books.length);
        assertEquals("三体", books[0].get("title"));
        assertTrue(((List<?>) books[0].get("sources")).size() >= 2);
        assertEquals(true, books[0].get("ebookAvailable"));

        Map<String, Object> rules = rest.getForObject(url("/api/rules"), Map.class);
        assertTrue(rules.containsKey("R-HOLD-02"));
        assertTrue(rules.containsKey("R-TRANSFER-03"));

        Map<String, Object> openapi = rest.getForObject(url("/v3/api-docs"), Map.class);
        assertEquals("3.0.1", openapi.get("openapi"));
        assertNotNull(openapi.get("paths"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void endToEndApiFlow() {
        Map<String, Object>[] books = rest.getForObject(
                url("/api/catalog/search?q=三体&pickup=CENTRAL"), Map[].class);
        Long bookId = ((Number) books[0].get("bookId")).longValue();

        // P0001 本馆预约 -> READY
        var resp1 = rest.postForEntity(url("/api/books/" + bookId + "/holds"),
                Map.of("cardNo", "P0001", "pickupLibrary", "CENTRAL"), Map.class);
        assertEquals(200, resp1.getStatusCode().value());
        Long h1 = ((Number) resp1.getBody().get("id")).longValue();
        assertEquals("READY", resp1.getBody().get("status"));
        String barcode = (String) resp1.getBody().get("reservedBarcode");

        // 取书借出
        var co = rest.postForEntity(url("/api/holds/" + h1 + "/checkout"),
                Map.of("barcode", barcode), Map.class);
        assertEquals(200, co.getStatusCode().value());
        assertEquals("ACTIVE", co.getBody().get("status"));

        // P0002 跨馆预约 -> QUEUED + 调拨
        var resp2 = rest.postForEntity(url("/api/books/" + bookId + "/holds"),
                Map.of("cardNo", "P0002", "pickupLibrary", "CENTRAL"), Map.class);
        Long h2 = ((Number) resp2.getBody().get("id")).longValue();
        assertEquals("QUEUED", resp2.getBody().get("status"));

        Map<String, Object>[] transfers = rest.getForObject(url("/api/transfers"), Map[].class);
        Map<String, Object> transfer = java.util.Arrays.stream(transfers)
                .filter(t -> "CREATED".equals(t.get("status"))).findFirst().orElseThrow();
        Long transferId = ((Number) transfer.get("id")).longValue();

        rest.postForEntity(url("/api/transfers/" + transferId + "/pickup"),
                Map.of("packageNo", "PKG-API-01"), Map.class);
        var arrived = rest.postForEntity(url("/api/transfers/" + transferId + "/arrive"),
                Map.of(), Map.class);
        assertEquals(200, arrived.getStatusCode().value());

        Map<String, Object> hold2 = rest.getForObject(url("/api/holds/" + h2), Map.class);
        assertEquals("READY", hold2.get("status"));

        // 到馆通知已生成
        Map<String, Object>[] notices = rest.getForObject(
                url("/api/patrons/P0002/notices"), Map[].class);
        assertTrue(java.util.Arrays.stream(notices)
                .anyMatch(n -> "READY_FOR_PICKUP".equals(n.get("type"))));
    }

    @Test
    void businessRuleViolation_returnsRuleRef() {
        // 重复预约返回 400 且带 ruleRef
        var first = rest.postForEntity(url("/api/books/1/holds"),
                Map.of("cardNo", "P0001", "pickupLibrary", "CENTRAL"), Map.class);
        assertEquals(200, first.getStatusCode().value());
        var second = rest.postForEntity(url("/api/books/1/holds"),
                Map.of("cardNo", "P0001", "pickupLibrary", "CENTRAL"), Map.class);
        assertEquals(400, second.getStatusCode().value());
        assertEquals("R-HOLD-01", second.getBody().get("ruleRef"));
    }
}
