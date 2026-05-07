package com.wms.po.e2e.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate test runner for E2E tests (3-Layer Approach - Layer 1).
 *
 * <p>Feature files are located in src/test/resources/com/wms/po/e2e/karate/
 * and are discovered using relativeTo(getClass()) which looks for features
 * in the same package location as this runner class.
 *
 * <p>Run all tests:
 * <pre>
 *   mvn verify -pl po-test -Dkarate.env=local
 *   mvn verify -pl po-test -Dkarate.env=ci
 * </pre>
 *
 * <p>Run by flow:
 * <pre>
 *   mvn verify -pl po-test -Dkarate.options="--tags @F1"
 *   mvn verify -pl po-test -Dkarate.options="--tags @F2"
 *   mvn verify -pl po-test -Dkarate.options="--tags @F3"
 *   mvn verify -pl po-test -Dkarate.options="--tags @F10"
 * </pre>
 */
public class KarateTestRunner {

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        return Karate.run().relativeTo(getClass()).tags("@F1");
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        return Karate.run().relativeTo(getClass()).tags("@F2");
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        return Karate.run().relativeTo(getClass()).tags("@F3");
    }

    @Karate.Test
    Karate testF10_Compensation() {
        return Karate.run().relativeTo(getClass()).tags("@F10", "@Compensation");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run().relativeTo(getClass()).tags("@Happy");
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run().relativeTo(getClass()).tags("@Unhappy");
    }

    @Karate.Test
    Karate testCompensation() {
        return Karate.run().relativeTo(getClass()).tags("@Compensation", "@Saga");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run().relativeTo(getClass()).tags("@P1");
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run().relativeTo(getClass()).tags("@P1", "@P2");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run().relativeTo(getClass()).tags("@API");
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run().relativeTo(getClass()).tags("@EDI");
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run().relativeTo(getClass()).tags("@Job");
    }

    // ═══════════════════════════════════════════════════════════
    // Legacy methods (backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testSmoke() {
        return Karate.run().relativeTo(getClass()).tags("@smoke");
    }

    @Karate.Test
    Karate testPopulate() {
        return Karate.run().relativeTo(getClass()).tags("@populate");
    }

    @Karate.Test
    Karate testSaga() {
        return Karate.run().relativeTo(getClass()).tags("@saga");
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run().relativeTo(getClass()).tags("@crud");
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run().relativeTo(getClass()).tags("@Regression");
    }
}
