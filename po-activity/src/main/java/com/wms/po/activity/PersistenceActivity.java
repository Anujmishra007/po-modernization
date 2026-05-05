package com.wms.po.activity;

import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.MappingResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activity for database persistence operations with compensation methods
 */
@ActivityInterface
public interface PersistenceActivity {

    /**
     * Create receipt header
     * @return the generated receipt key
     */
    @ActivityMethod
    String createReceiptHeader(MappingResult mapping);

    /**
     * Delete receipt header (COMPENSATION)
     */
    @ActivityMethod
    void deleteReceiptHeader(String receiptKey);

    /**
     * Create receipt details
     * @return list of generated detail keys
     */
    @ActivityMethod
    List<String> createReceiptDetails(String receiptKey, List<DetailMapping> details);

    /**
     * Delete receipt details (COMPENSATION)
     */
    @ActivityMethod
    void deleteReceiptDetails(List<String> detailKeys);

    /**
     * Update receipt status
     */
    @ActivityMethod
    void updateReceiptStatus(String receiptKey, String status);
}
