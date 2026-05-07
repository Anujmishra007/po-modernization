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
 * Gatling Performance Simulation for PO Creation
 *
 * Tests:
 * - Single-line PO creation throughput
 * - Multi-line PO creation (50 lines)
 * - Concurrent PO creation under load
 *
 * Performance Targets:
 * - Response time: < 500ms (p95)
 * - Throughput: > 100 req/sec
 * - Error rate: < 1%
 */
public class POCreationSimulation extends Simulation {

    // Configuration
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS = Integer.parseInt(System.getProperty("users", "50"));
    private static final int DURATION_SECONDS = Integer.parseInt(System.getProperty("duration", "60"));
    private static final int RAMP_SECONDS = Integer.parseInt(System.getProperty("ramp", "10"));

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling/Performance-Test");

    // Data Feeders
    Iterator<Map<String, Object>> poFeeder = Stream.generate((Supplier<Map<String, Object>>) () -> {
        String poKey = "PO-PERF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Map.of(
            "poKey", poKey,
            "storerKey", "STR-" + String.format("%03d", new Random().nextInt(10) + 1),
            "facility", "WH01",
            "sku", "SKU-" + String.format("%05d", new Random().nextInt(15) + 1),
            "qty", new Random().nextInt(100) + 1
        );
    }).iterator();

    // Single-line PO Creation Scenario
    ScenarioBuilder singleLinePO = scenario("Single-Line PO Creation")
        .feed(poFeeder)
        .exec(http("Create Single-Line PO")
            .post("/api/v1/po")
            .body(StringBody("""
                {
                    "poKey": "#{poKey}",
                    "storerKey": "#{storerKey}",
                    "facility": "#{facility}",
                    "poType": "STANDARD",
                    "status": "0",
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
            .check(jsonPath("$.poKey").exists())
        );

    // Multi-line PO Creation Scenario (50 lines)
    ScenarioBuilder multiLinePO = scenario("Multi-Line PO Creation (50 lines)")
        .feed(poFeeder)
        .exec(http("Create Multi-Line PO")
            .post("/api/v1/po")
            .body(StringBody(session -> {
                StringBuilder details = new StringBuilder("[");
                for (int i = 1; i <= 50; i++) {
                    if (i > 1) details.append(",");
                    details.append(String.format("""
                        {
                            "lineNumber": %d,
                            "sku": "SKU-%05d",
                            "qtyOrdered": %d,
                            "uom": "EA"
                        }
                        """, i, (i % 15) + 1, new Random().nextInt(100) + 1));
                }
                details.append("]");

                return String.format("""
                    {
                        "poKey": "%s-MULTI",
                        "storerKey": "%s",
                        "facility": "%s",
                        "poType": "STANDARD",
                        "status": "0",
                        "details": %s
                    }
                    """,
                    session.getString("poKey"),
                    session.getString("storerKey"),
                    session.getString("facility"),
                    details.toString()
                );
            }))
            .check(status().in(200, 201))
            .check(jsonPath("$.poKey").exists())
        );

    // Burst Load Scenario
    ScenarioBuilder burstLoad = scenario("Burst Load - Rapid PO Creation")
        .feed(poFeeder)
        .exec(http("Burst Create PO")
            .post("/api/v1/po")
            .body(StringBody("""
                {
                    "poKey": "#{poKey}-BURST",
                    "storerKey": "#{storerKey}",
                    "facility": "#{facility}",
                    "poType": "STANDARD",
                    "status": "0",
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
        );

    {
        setUp(
            // Steady load for single-line POs
            singleLinePO.injectOpen(
                rampUsers(USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 2.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Lower volume for multi-line POs (more expensive)
            multiLinePO.injectOpen(
                rampUsers(USERS / 5).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantUsersPerSec(USERS / 10.0).during(Duration.ofSeconds(DURATION_SECONDS))
            ),

            // Burst test at the end
            burstLoad.injectOpen(
                nothingFor(Duration.ofSeconds(DURATION_SECONDS / 2)),
                atOnceUsers(USERS)
            )
        )
        .protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95.0).lt(500),
            global().successfulRequests().percent().gt(99.0),
            forAll().failedRequests().percent().lt(1.0)
        );
    }
}
