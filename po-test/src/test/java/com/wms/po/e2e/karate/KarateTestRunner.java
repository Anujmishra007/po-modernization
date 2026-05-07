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
 *
 * <p>Uses relativeTo(KarateTestRunner.class) for reliable classpath resolution in CI.
 */
public class KarateTestRunner {

    // Feature subdirectory paths (relative to this class)
    private static final String F1 = "features/f1-po-creation";
    private static final String F2 = "features/f2-asn-population";
    private static final String F3 = "features/f3-receipt-finalization";
    private static final String F4 = "features/f4-cross-dock";
    private static final String F5 = "features/f5-lottable-tracking";
    private static final String F6 = "features/f6-putaway-task";
    private static final String F7 = "features/f7-trade-return";
    private static final String F8 = "features/f8-cancellation";
    private static final String F9 = "features/f9-archival";
    private static final String F10 = "features/f10-compensation";

    // All feature paths combined (root-level + subdirectories)
    private static final String[] ALL_FEATURES = {
        "health.feature",
        "po-crud.feature",
        "populate.feature",
        "populate-po.feature",
        "receipt.feature",
        "finalize-receipt.feature",
        "variation.feature",
        "saga-compensation.feature",
        "trade-return.feature",
        "nike-workflow.feature",
        "hm-workflow.feature",
        "korea-workflow.feature",
        "india-workflow.feature",
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10
    };

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        return Karate.run(F1).relativeTo(KarateTestRunner.class).tags("@F1");
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        return Karate.run(F2).relativeTo(KarateTestRunner.class).tags("@F2");
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        return Karate.run(F3).relativeTo(KarateTestRunner.class).tags("@F3");
    }

    @Karate.Test
    Karate testF4_CrossDock() {
        return Karate.run(F4).relativeTo(KarateTestRunner.class).tags("@F4");
    }

    @Karate.Test
    Karate testF5_LottableTracking() {
        return Karate.run(F5).relativeTo(KarateTestRunner.class).tags("@F5");
    }

    @Karate.Test
    Karate testF6_PutawayTask() {
        return Karate.run(F6).relativeTo(KarateTestRunner.class).tags("@F6");
    }

    @Karate.Test
    Karate testF7_TradeReturn() {
        return Karate.run(F7).relativeTo(KarateTestRunner.class).tags("@F7");
    }

    @Karate.Test
    Karate testF8_Cancellation() {
        return Karate.run(F8).relativeTo(KarateTestRunner.class).tags("@F8");
    }

    @Karate.Test
    Karate testF9_Archival() {
        return Karate.run(F9).relativeTo(KarateTestRunner.class).tags("@F9");
    }

    @Karate.Test
    Karate testF10_Compensation() {
        return Karate.run(F10).relativeTo(KarateTestRunner.class).tags("@F10", "@Compensation");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@Happy");
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@Unhappy");
    }

    @Karate.Test
    Karate testCompensation() {
        return Karate.run(F10, "saga-compensation.feature").relativeTo(KarateTestRunner.class).tags("@Compensation", "@Saga");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@P1");
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@P1", "@P2");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@API");
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@EDI");
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@Job");
    }

    // ═══════════════════════════════════════════════════════════
    // Legacy methods (backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testSmoke() {
        return Karate.run("health.feature").relativeTo(KarateTestRunner.class).tags("@smoke");
    }

    @Karate.Test
    Karate testPopulate() {
        return Karate.run("populate.feature", "populate-po.feature").relativeTo(KarateTestRunner.class).tags("@populate");
    }

    @Karate.Test
    Karate testSaga() {
        return Karate.run(F10, "saga-compensation.feature").relativeTo(KarateTestRunner.class).tags("@saga");
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run("po-crud.feature").relativeTo(KarateTestRunner.class).tags("@crud");
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run(ALL_FEATURES).relativeTo(KarateTestRunner.class).tags("@Regression");
    }
}
