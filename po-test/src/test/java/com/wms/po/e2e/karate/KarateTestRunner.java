package com.wms.po.e2e.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate test runner for E2E tests (3-Layer Approach - Layer 1).
 *
 * <p>Feature files are located in src/test/resources/com/wms/po/e2e/karate/
 *
 * <p>Run all tests:
 * <pre>
 *   mvn verify -pl po-test -Dkarate.env=local
 *   mvn verify -pl po-test -Dkarate.env=ci
 * </pre>
 */
public class KarateTestRunner {

    // Explicit paths to work around Karate classpath scanning issues in CI
    private static final String BASE = "classpath:com/wms/po/e2e/karate";
    private static final String F1 = BASE + "/features/f1-po-creation";
    private static final String F2 = BASE + "/features/f2-asn-population";
    private static final String F3 = BASE + "/features/f3-receipt-finalization";
    private static final String F4 = BASE + "/features/f4-cross-dock";
    private static final String F5 = BASE + "/features/f5-lottable-tracking";
    private static final String F6 = BASE + "/features/f6-putaway-task";
    private static final String F7 = BASE + "/features/f7-trade-return";
    private static final String F8 = BASE + "/features/f8-cancellation";
    private static final String F9 = BASE + "/features/f9-archival";
    private static final String F10 = BASE + "/features/f10-compensation";

    // All feature paths combined
    private static final String[] ALL_FEATURES = {
        BASE + "/health.feature",
        BASE + "/po-crud.feature",
        BASE + "/populate.feature",
        BASE + "/populate-po.feature",
        BASE + "/receipt.feature",
        BASE + "/finalize-receipt.feature",
        BASE + "/variation.feature",
        BASE + "/saga-compensation.feature",
        BASE + "/trade-return.feature",
        BASE + "/nike-workflow.feature",
        BASE + "/hm-workflow.feature",
        BASE + "/korea-workflow.feature",
        BASE + "/india-workflow.feature",
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10
    };

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run(ALL_FEATURES);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        return Karate.run(F1).tags("@F1");
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        return Karate.run(F2).tags("@F2");
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        return Karate.run(F3).tags("@F3");
    }

    @Karate.Test
    Karate testF10_Compensation() {
        return Karate.run(F10).tags("@F10", "@Compensation");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run(ALL_FEATURES).tags("@Happy");
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run(ALL_FEATURES).tags("@Unhappy");
    }

    @Karate.Test
    Karate testCompensation() {
        return Karate.run(F10, BASE + "/saga-compensation.feature").tags("@Compensation", "@Saga");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run(ALL_FEATURES).tags("@P1");
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run(ALL_FEATURES).tags("@P1", "@P2");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run(ALL_FEATURES).tags("@API");
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run(ALL_FEATURES).tags("@EDI");
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run(ALL_FEATURES).tags("@Job");
    }

    // ═══════════════════════════════════════════════════════════
    // Legacy methods (backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testSmoke() {
        return Karate.run(BASE + "/health.feature").tags("@smoke");
    }

    @Karate.Test
    Karate testPopulate() {
        return Karate.run(BASE + "/populate.feature", BASE + "/populate-po.feature").tags("@populate");
    }

    @Karate.Test
    Karate testSaga() {
        return Karate.run(F10, BASE + "/saga-compensation.feature").tags("@saga");
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run(BASE + "/po-crud.feature").tags("@crud");
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run(ALL_FEATURES).tags("@Regression");
    }
}
