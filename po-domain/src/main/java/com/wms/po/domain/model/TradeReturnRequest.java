package com.wms.po.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Request for Trade Return workflow.
 * Represents an ASN to Sales Order population request.
 *
 * Replaces: SP-004 lsp_ASN_PopulateSOs_Wrapper (input parameters)
 */
@Data
@Builder
public class TradeReturnRequest {

    /**
     * Receipt key (ASN) to process
     */
    private String receiptKey;

    /**
     * Storer key (owner)
     */
    private String storerKey;

    /**
     * Country code for routing
     */
    private String countryCode;

    /**
     * Facility/warehouse code
     */
    private String facility;

    /**
     * User initiating the request
     */
    private String userId;

    /**
     * External reference (from source system)
     */
    private String externalReference;

    /**
     * Trade return type (RETURN, EXCHANGE, RMA, etc.)
     */
    private String returnType;

    /**
     * Reason code for the return
     */
    private String reasonCode;

    /**
     * Customer/ship-to code
     */
    private String customerCode;

    /**
     * Original order number (if applicable)
     */
    private String originalOrderNumber;

    /**
     * Carrier code for return shipment
     */
    private String carrierCode;

    /**
     * Priority (1=High, 2=Medium, 3=Low)
     */
    private Integer priority;

    /**
     * Specific line numbers to process (null = all lines)
     */
    private List<Integer> lineNumbers;

    /**
     * Flag to auto-release for outbound processing
     */
    @Builder.Default
    private boolean autoRelease = false;

    /**
     * Flag to validate quality inspection required
     */
    @Builder.Default
    private boolean requireQualityCheck = true;

    /**
     * Custom field 1
     */
    private String susr1;

    /**
     * Custom field 2
     */
    private String susr2;

    /**
     * Custom field 3
     */
    private String susr3;

    /**
     * Check if request has minimum required fields
     */
    public boolean isValid() {
        return receiptKey != null && !receiptKey.isEmpty() &&
               storerKey != null && !storerKey.isEmpty() &&
               countryCode != null && !countryCode.isEmpty();
    }
}
