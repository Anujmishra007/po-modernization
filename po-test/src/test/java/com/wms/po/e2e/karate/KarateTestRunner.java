package com.wms.po.e2e.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate test runner for E2E tests.
 *
 * Run all tests:
 *   mvn test -Dtest=KarateTestRunner -Dkarate.env=local
 *
 * Run by tag:
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @smoke" -Dkarate.env=local
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @populate" -Dkarate.env=local
 *   mvn test -Dtest=KarateTestRunner -Dkarate.options="--tags @saga" -Dkarate.env=local
 */
public class KarateTestRunner {

    @Karate.Test
    Karate testAll() {
        return Karate.run()
            .relativeTo(getClass());
    }

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
}
