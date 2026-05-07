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
 * Gatling Performance Simulation for Full E2E Workflow
 *
 * Tests the complete PO lifecycle:
 * 1. PO Creation
 * 2. ASN Population
 * 3. Receipt Creation
 * 4. Receipt Finalization
 * 5. Inventory Verification
 *
 * Performance Targets:
 * - Full workflow: < 3000ms (p95)
 * - Throughput: > 20 complete workflows/sec
 * - Error rate: < 2%
 */
public class E2EWorkflowSimulation extends Simulation {

    // Configuration
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS = Integer.parseInt(System.getProperty("users", "20"));
    private static final int DURATION_SECONDS = Integer.parseInt(System.getProperty("duration", "120"));
    private static final int RAMP_SECONDS = Integer.parseInt(System.getProperty("ramp", "20"));

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling/E2E-Performance-Test");

    // Data Feeders
    Iterator<Map<String, Object>> workflowFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Map.of(
            "uniqueId", uniqueId,
            "poKey", "PO-E2E-" + uniqueId,
            "asnKey", "ASN-E2E-" + uniqueId,
            "receiptKey", "RCV-E2E-" + uniqueId,
            "storerKey", "STR-" + String.format("%03d", new Random().nextInt(10) + 1),
            "facility", "WH01",
            "sku", "SKU-" + String.format("%05d", new Random().nextInt(15) + 1),
            "qty", new Random().nextInt(100) + 10
        );
    }).iterator();

    // Step 1: Create PO
    ChainBuilder createPO = exec(http("1. Create PO")
        .post("/api/v1/po")
        .body(StringBody("""
            {
                "poKey": "#{poKey}",
                "storerKey": "#{storerKey}",
                "facility": "#{facility}",
                "poType": "STANDARD",
                "status": "0",
                "expectedDate": "2026-05-15",
                "details": [
                    {
                        "lineNumber": 1,
                        "sku": "#{sku}",
                        "qtyOrdered": #{qty},
                        "uom": "EA"
                    }
                ]
            }
            """))
        .check(status().in(200, 201))
        .check(jsonPath("$.poKey").saveAs("createdPOKey"))
    );

    // Step 2: Create ASN and Populate
    ChainBuilder populateASN = exec(http("2. Populate ASN")
        .post("/api/v1/asn/populate")
        .body(StringBody("""
            {
                "asnKey": "#{asnKey}",
                "poKey": "#{createdPOKey}",
                "storerKey": "#{storerKey}",
                "facility": "#{facility}",
                "carrierCode": "FEDEX",
                "details": [
                    {
                        "lineNumber": 1,
                        "sku": "#{sku}",
                        "qtyShipped": #{qty},
                        "uom": "EA"
                    }
                ]
            }
            """))
        .check(status().in(200, 201, 202))
        .check(jsonPath("$.receiptKey").saveAs("populatedReceiptKey"))
    );

    // Step 3: Verify Receipt Created
    ChainBuilder verifyReceipt = exec(http("3. Verify Receipt")
        .get("/api/v1/receipts/#{populatedReceiptKey}")
        .check(status().is(200))
        .check(jsonPath("$.status").exists())
    );

    // Step 4: Finalize Receipt
    ChainBuilder finalizeReceipt = exec(http("4. Finalize Receipt")
        .post("/api/v1/receipts/#{populatedReceiptKey}/finalize")
        .body(StringBody("""
            {
                "finalizeMode": "FULL",
                "autoReleasePutaway": true,
                "applyHolds": false
            }
            """))
        .check(status().in(200, 202))
    );

    // Step 5: Verify Inventory Posted
    ChainBuilder verifyInventory = exec(http("5. Verify Inventory")
        .get("/api/v1/inventory/check")
        .queryParam("storerKey", "#{storerKey}")
        .queryParam("sku", "#{sku}")
        .check(status().is(200))
    );

    // Step 6: Verify PO Status Updated
    ChainBuilder verifyPOStatus = exec(http("6. Verify PO Status")
        .get("/api/v1/po/#{createdPOKey}")
        .check(status().is(200))
        .check(jsonPath("$.qtyReceived").exists())
    );

    // Full E2E Workflow Scenario
    ScenarioBuilder fullE2EWorkflow = scenario("Full E2E PO Lifecycle")
        .feed(workflowFeeder)
        .exec(createPO)
        .pause(Duration.ofMillis(100))
        .exec(populateASN)
        .pause(Duration.ofMillis(200))
        .exec(verifyReceipt)
        .pause(Duration.ofMillis(100))
        .exec(finalizeReceipt)
        .pause(Duration.ofMillis(300))
        .exec(verifyInventory)
        .exec(verifyPOStatus);

    // Concurrent Multi-User Workflow
    ScenarioBuilder concurrentWorkflow = scenario("Concurrent E2E Workflows")
        .feed(workflowFeeder)
        .exec(createPO)
        .exec(populateASN)
        .exec(finalizeReceipt);

    // Stress Test - Rapid Fire
    ScenarioBuilder stressTest = scenario("Stress Test - Rapid E2E")
        .feed(workflowFeeder)
        .exec(createPO)
        .exec(populateASN)
        .exec(finalizeReceipt)
        .exec(verifyPOStatus);

    {
        setUp(
            // Standard E2E workflow load
            fullE2EWorkflow.injectOpen(
                rampUsers(USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 5.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Concurrent users (simulate warehouse operations)
            concurrentWorkflow.injectOpen(
                nothingFor(Duration.ofSeconds(10)),
                rampUsers(USERS / 2).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 4.0).during(Duration.ofSeconds(DURATION_SECONDS - 10))
            ),

            // Stress test at the end
            stressTest.injectOpen(
                nothingFor(Duration.ofSeconds(DURATION_SECONDS / 2)),
                rampUsers(USERS * 2).during(Duration.ofSeconds(RAMP_SECONDS / 2))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            // E2E workflow is complex, allow up to 3 seconds total
            global().responseTime().percentile(95.0).lt(3000),
            global().successfulRequests().percent().gt(98.0),
            forAll().failedRequests().percent().lt(2.0)
        );
    }
}
