package com.wms.po.e2e;

import com.intuit.karate.junit5.Karate;

class KarateRunner {

    @Karate.Test
    Karate testAll() {
        return Karate.run()
                .relativeTo(getClass())
                .tags("~@ignore");
    }

    @Karate.Test
    Karate testHealth() {
        return Karate.run("health.feature")
                .relativeTo(getClass());
    }

    // Purchase Order tests - currently @ignore until API endpoints are implemented
    // Uncomment when PO APIs are ready
    // @Karate.Test
    // Karate testPurchaseOrder() {
    //     return Karate.run("purchase-order.feature")
    //             .relativeTo(getClass())
    //             .tags("~@ignore");
    // }
}
