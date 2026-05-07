package com.wms.po.e2e.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate test runner for E2E tests (3-Layer Approach - Layer 1).
 *
 * <p>Run all tests:
 * <pre>
 *   mvn test -Dtest=KarateTestRunner -Dkarate.env=local
 *   mvn test -Dtest=KarateTestRunner -Dkarate.env=docker
 * </pre>
 *
 * <p>Run by flow:
 * <pre>
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @F1"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @F2"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @F3"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @F10"
 * </pre>
 *
 * <p>Run by type:
 * <pre>
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @Happy"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @Unhappy"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @Compensation"
 * </pre>
 *
 * <p>Run by priority:
 * <pre>
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @P1"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @P1 or @P2"
 * </pre>
 *
 * <p>Run by entry point:
 * <pre>
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @API"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @EDI"
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @Job"
 * </pre>
 */
public class KarateTestRunner {

    // Base classpath for all Karate features
    private static final String FEATURES_PATH = "classpath:com/wms/po/e2e/karate";

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run(FEATURES_PATH);
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        return Karate.run(FEATURES_PATH).tags("@F1");
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        return Karate.run(FEATURES_PATH).tags("@F2");
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        return Karate.run(FEATURES_PATH).tags("@F3");
    }

    @Karate.Test
    Karate testF10_Compensation() {
        return Karate.run(FEATURES_PATH).tags("@F10 or @Compensation");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run(FEATURES_PATH).tags("@Happy");
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run(FEATURES_PATH).tags("@Unhappy");
    }

    @Karate.Test
    Karate testCompensation() {
        return Karate.run(FEATURES_PATH).tags("@Compensation or @Saga");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run(FEATURES_PATH).tags("@P1");
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run(FEATURES_PATH).tags("@P1 or @P2");
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run(FEATURES_PATH).tags("@API");
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run(FEATURES_PATH).tags("@EDI");
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run(FEATURES_PATH).tags("@Job");
    }

    // ═══════════════════════════════════════════════════════════
    // Legacy methods (backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testSmoke() {
        return Karate.run(FEATURES_PATH).tags("@smoke");
    }

    @Karate.Test
    Karate testPopulate() {
        return Karate.run(FEATURES_PATH).tags("@populate");
    }

    @Karate.Test
    Karate testSaga() {
        return Karate.run(FEATURES_PATH).tags("@saga");
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run(FEATURES_PATH).tags("@crud");
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run(FEATURES_PATH).tags("@Regression");
    }
}
