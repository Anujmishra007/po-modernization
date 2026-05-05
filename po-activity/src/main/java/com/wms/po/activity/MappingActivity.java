package com.wms.po.activity;

import com.wms.po.domain.model.LottableResult;
import com.wms.po.domain.model.MappingResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for mapping PO to ASN/Receipt
 */
@ActivityInterface
public interface MappingActivity {

    /**
     * Map PO fields to ASN/Receipt structure
     */
    @ActivityMethod
    MappingResult mapPOToASN(PopulateRequest request, VariationContext context);

    /**
     * Apply lottable rules based on context
     */
    @ActivityMethod
    LottableResult applyLottables(MappingResult mapping, VariationContext context);
}
