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

    // F1: PO Creation feature files
    private static final String[] F1_FEATURES = {
        "features/f1-po-creation/po-creation-happy.feature",
        "features/f1-po-creation/po-creation-unhappy.feature",
        "features/f1-po-creation/po-creation-edge.feature",
        "features/f1-po-creation/po-creation-error.feature",
        "features/f1-po-creation/po-creation-compensation.feature",
        "features/f1-po-creation/po-creation-rdt.feature",
        "features/f1-po-creation/po-creation-trigger.feature"
    };

    // F2: ASN Population feature files
    private static final String[] F2_FEATURES = {
        "features/f2-asn-population/asn-population-happy.feature",
        "features/f2-asn-population/asn-population-unhappy.feature",
        "features/f2-asn-population/asn-population-edge.feature"
    };

    // F3: Receipt Finalization feature files
    private static final String[] F3_FEATURES = {
        "features/f3-receipt-finalization/receipt-finalization-happy.feature",
        "features/f3-receipt-finalization/receipt-finalization-unhappy.feature",
        "features/f3-receipt-finalization/receipt-finalization-edge.feature"
    };

    // F4: Cross Dock feature files
    private static final String[] F4_FEATURES = {
        "features/f4-cross-dock/cross-dock-happy.feature",
        "features/f4-cross-dock/cross-dock-unhappy.feature",
        "features/f4-cross-dock/cross-dock-edge.feature"
    };

    // F5: Lottable Tracking feature files
    private static final String[] F5_FEATURES = {
        "features/f5-lottable-tracking/lottable-tracking.feature"
    };

    // F6: Putaway Task feature files
    private static final String[] F6_FEATURES = {
        "features/f6-putaway-task/putaway-task.feature"
    };

    // F7: Trade Return feature files
    private static final String[] F7_FEATURES = {
        "features/f7-trade-return/trade-return.feature"
    };

    // F8: Cancellation feature files
    private static final String[] F8_FEATURES = {
        "features/f8-cancellation/cancellation.feature"
    };

    // F9: Archival feature files
    private static final String[] F9_FEATURES = {
        "features/f9-archival/archival.feature"
    };

    // F10: Compensation feature files
    private static final String[] F10_FEATURES = {
        "features/f10-compensation/saga-compensation.feature",
        "features/f10-compensation/saga-compensation-populate.feature",
        "features/f10-compensation/saga-compensation-finalize.feature",
        "features/f10-compensation/saga-compensation-infrastructure.feature",
        "features/f10-compensation/saga-compensation-crossflow.feature"
    };

    // Root-level feature files
    private static final String[] ROOT_FEATURES = {
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
        "india-workflow.feature"
    };

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        // Run all F1 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F1_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        // Run all F2 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F2_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        // Run all F3 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F3_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF4_CrossDock() {
        // Run all F4 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F4_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF5_LottableTracking() {
        // Run all F5 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F5_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF6_PutawayTask() {
        // Run all F6 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F6_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF7_TradeReturn() {
        // Run all F7 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F7_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF8_Cancellation() {
        // Run all F8 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F8_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF9_Archival() {
        // Run all F9 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F9_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testF10_Compensation() {
        // Run all F10 feature files - tag filtering removed as feature-level tags don't filter scenarios
        return Karate.run(F10_FEATURES).relativeTo(KarateTestRunner.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@Happy");
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@Unhappy");
    }

    @Karate.Test
    Karate testCompensation() {
        // Run all F10 compensation feature files
        return Karate.run(F10_FEATURES).relativeTo(KarateTestRunner.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@P1");
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@P1", "@P2");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@API");
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@EDI");
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@Job");
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
        // Run all F10 saga/compensation feature files
        return Karate.run(F10_FEATURES).relativeTo(KarateTestRunner.class);
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run("po-crud.feature").relativeTo(KarateTestRunner.class).tags("@crud");
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run(ROOT_FEATURES).relativeTo(KarateTestRunner.class).tags("@Regression");
    }
}
