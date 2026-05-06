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

    // ═══════════════════════════════════════════════════════════
    // Run All Tests
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAll() {
        return Karate.run()
            .relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Flow
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testF1_POCreation() {
        return Karate.run()
            .tags("@F1")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testF2_ASNPopulation() {
        return Karate.run()
            .tags("@F2")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testF3_ReceiptFinalization() {
        return Karate.run()
            .tags("@F3")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testF10_Compensation() {
        return Karate.run()
            .tags("@F10", "@Compensation")
            .relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Type
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testHappyPath() {
        return Karate.run()
            .tags("@Happy")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testUnhappyPath() {
        return Karate.run()
            .tags("@Unhappy")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testCompensation() {
        return Karate.run()
            .tags("@Compensation", "@Saga")
            .relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Priority
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testP1Critical() {
        return Karate.run()
            .tags("@P1")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testP1andP2() {
        return Karate.run()
            .tags("@P1", "@P2")
            .relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Run by Entry Point
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testAPIEntry() {
        return Karate.run()
            .tags("@API")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testEDIEntry() {
        return Karate.run()
            .tags("@EDI")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testJobEntry() {
        return Karate.run()
            .tags("@Job")
            .relativeTo(getClass());
    }

    // ═══════════════════════════════════════════════════════════
    // Legacy methods (backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Karate.Test
    Karate testSmoke() {
        return Karate.run()
            .tags("@smoke")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testPopulate() {
        return Karate.run()
            .tags("@populate")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testSaga() {
        return Karate.run()
            .tags("@saga")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testCRUD() {
        return Karate.run()
            .tags("@crud")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testRegression() {
        return Karate.run()
            .tags("@Regression")
            .relativeTo(getClass());
    }
}
