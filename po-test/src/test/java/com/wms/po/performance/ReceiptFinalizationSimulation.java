package com.wms.po.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling Performance Simulation for Receipt Finalization
 *
 * Tests the critical receipt finalization flow which includes:
 * - Status updates
 * - Inventory posting
 * - Hold application
 * - Putaway task creation
 * - PO quantity updates
 *
 * Performance Targets:
 * - Response time: < 1000ms (p95) - complex operation
 * - Throughput: > 50 req/sec
 * - Error rate: < 1%
 */
public class ReceiptFinalizationSimulation extends Simulation {

    // Configuration
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS = Integer.parseInt(System.getProperty("users", "30"));
    private static final int DURATION_SECONDS = Integer.parseInt(System.getProperty("duration", "60"));
    private static final int RAMP_SECONDS = Integer.parseInt(System.getProperty("ramp", "10"));

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling/Performance-Test");

    // Data Feeders for receipts
    Iterator<Map<String, Object>> receiptFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        String receiptKey = "RCV-PERF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Map.of(
            "receiptKey", receiptKey,
            "storerKey", "STR-" + String.format("%03d", new Random().nextInt(10) + 1),
            "facility", "WH01",
            "poKey", "PO-" + String.format("%06d", new Random().nextInt(1000) + 1)
        );
    }).iterator();

    // Setup: Create Receipt (pre-condition for finalization)
    ChainBuilder createReceipt = exec(http("Create Receipt")
        .post("/api/v1/receipts")
        .body(StringBody("""
            {
                "receiptKey": "#{receiptKey}",
                "storerKey": "#{storerKey}",
                "facility": "#{facility}",
                "poKey": "#{poKey}",
                "receiptType": "STANDARD",
                "status": "5",
                "details": [
                    {
                        "lineNumber": 1,
                        "sku": "SKU-00001",
                        "qtyReceived": 100,
                        "uom": "EA",
                        "location": "RCV-DOCK-01"
                    }
                ]
            }
            """))
        .check(status().in(200, 201))
        .check(jsonPath("$.receiptKey").saveAs("createdReceiptKey"))
    );

    // Finalize Receipt Scenario
    ScenarioBuilder finalizeReceipt = scenario("Receipt Finalization")
        .feed(receiptFeeder)
        .exec(createReceipt)
        .pause(Duration.ofMillis(100))
        .exec(http("Finalize Receipt")
            .post("/api/v1/receipts/#{createdReceiptKey}/finalize")
            .body(StringBody("""
                {
                    "finalizeMode": "FULL",
                    "autoReleasePutaway": true,
                    "applyHolds": true
                }
                """))
            .check(status().in(200, 202))
            .check(jsonPath("$.status").is("9"))
        );

    // Partial Finalization Scenario
    ScenarioBuilder partialFinalize = scenario("Partial Receipt Finalization")
        .feed(receiptFeeder)
        .exec(createReceipt)
        .pause(Duration.ofMillis(100))
        .exec(http("Partial Finalize Receipt")
            .post("/api/v1/receipts/#{createdReceiptKey}/finalize")
            .body(StringBody("""
                {
                    "finalizeMode": "PARTIAL",
                    "lines": [1],
                    "autoReleasePutaway": false,
                    "applyHolds": false
                }
                """))
            .check(status().in(200, 202))
        );

    // Batch Finalization Scenario
    ScenarioBuilder batchFinalize = scenario("Batch Receipt Finalization")
        .feed(receiptFeeder)
        .repeat(5).on(
            exec(createReceipt)
                .exec(session -> session.set("receipt_" + session.getInt("counter"), session.getString("createdReceiptKey")))
        )
        .exec(http("Batch Finalize Receipts")
            .post("/api/v1/receipts/batch/finalize")
            .body(StringBody("""
                {
                    "receiptKeys": ["#{receipt_0}", "#{receipt_1}", "#{receipt_2}", "#{receipt_3}", "#{receipt_4}"],
                    "finalizeMode": "FULL",
                    "autoReleasePutaway": true
                }
                """))
            .check(status().in(200, 202))
        );

    // Workflow-based Finalization (via Temporal)
    ScenarioBuilder workflowFinalize = scenario("Workflow Finalization (Temporal)")
        .feed(receiptFeeder)
        .exec(createReceipt)
        .pause(Duration.ofMillis(100))
        .exec(http("Start Finalize Workflow")
            .post("/api/v1/workflows/finalize")
            .body(StringBody("""
                {
                    "receiptKey": "#{createdReceiptKey}",
                    "storerKey": "#{storerKey}",
                    "countryCode": "US",
                    "async": true
                }
                """))
            .check(status().in(200, 202))
            .check(jsonPath("$.workflowId").saveAs("workflowId"))
        )
        .pause(Duration.ofMillis(500))
        .exec(http("Check Workflow Status")
            .get("/api/v1/workflows/#{workflowId}/status")
            .check(status().is(200))
        );

    {
        setUp(
            // Standard finalization load
            finalizeReceipt.injectOpen(
                rampUsers(USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 3.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Partial finalization (lighter load)
            partialFinalize.injectOpen(
                rampUsers(USERS / 3).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 6.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Batch operations (even lighter - expensive)
            batchFinalize.injectOpen(
                rampUsers(USERS / 5).during(Duration.ofSeconds(RAMP_SECONDS * 2)),
                constantUsersPerSec(USERS / 15.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Workflow-based (async, can handle more)
            workflowFinalize.injectOpen(
                rampUsers(USERS / 2).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 4.0).during(Duration.ofSeconds(DURATION_SECONDS))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            // Finalization is complex, allow up to 1 second
            global().responseTime().percentile(95.0).lt(1000),
            global().successfulRequests().percent().gt(99.0),
            forAll().failedRequests().percent().lt(1.0)
        );
    }
}
