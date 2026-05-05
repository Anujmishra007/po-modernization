package com.wms.po.activity;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for running client-specific plugins
 */
@ActivityInterface
public interface PluginActivity {

    /**
     * Run pre-populate plugins
     */
    @ActivityMethod
    PluginResult runPrePopulate(PopulateRequest request, VariationContext context);

    /**
     * Run post-populate plugins
     */
    @ActivityMethod
    PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context);
}
