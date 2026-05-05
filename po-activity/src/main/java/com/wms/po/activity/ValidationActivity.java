package com.wms.po.activity;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for validation operations
 */
@ActivityInterface
public interface ValidationActivity {

    /**
     * Resolve the variation context from request parameters
     */
    @ActivityMethod
    VariationContext resolveContext(PopulateRequest request);

    /**
     * Validate the populate request
     */
    @ActivityMethod
    ValidationResult validate(PopulateRequest request, VariationContext context);

    /**
     * Resolve the variation context for trade return request
     */
    @ActivityMethod
    VariationContext resolveTradeReturnContext(TradeReturnRequest request);
}
