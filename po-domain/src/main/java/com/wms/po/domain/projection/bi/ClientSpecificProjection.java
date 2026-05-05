package com.wms.po.domain.projection.bi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BI Projection for Client-Specific Reports.
 *
 * Replaces: VW-014 - Client-Specific Views (18 views)
 * - V_BI_Nike_Inbound
 * - V_BI_Nike_CallOfModel
 * - V_BI_Nike_CRW
 * - V_BI_HM_Inbound
 * - V_BI_HM_QualityCheck
 * - V_BI_Adidas_Inbound
 * - V_BI_Adidas_Compliance
 * - V_BI_Columbia_UCC
 * - V_BI_Unilever_Inbound
 * - V_BI_Unilever_FIFO
 * - V_BI_NewLook_Inbound
 * - V_BI_DSG_Inbound
 * - V_BI_Mondelez_Expiry
 * - V_BI_APAC_Regional
 * - V_BI_Thailand_Customs
 * - V_BI_Taiwan_Inbound
 * - V_BI_India_GST
 * - V_BI_Singapore_Compliance
 */
public interface ClientSpecificProjection {

    // ═══════════════════════════════════════════════════════════════════════
    // Nike Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Nike_Inbound equivalent.
     */
    interface NikeInbound {
        String getReceiptKey();
        String getStorerKey();
        String getStyleCode();
        String getColorCode();
        String getSizeCode();
        String getModelName();
        String getSeasonCode();
        String getGenderCode();
        String getCategoryCode();
        BigDecimal getQtyExpected();
        BigDecimal getQtyReceived();
        BigDecimal getVarianceQty();
        String getFactoryCode();
        String getCountryOfOrigin();
        LocalDateTime getProductionDate();
        String getPoNumber();
        String getASNNumber();
        LocalDateTime getReceiptDate();
        String getQualityStatus();
        String getComplianceStatus();
    }

    /**
     * V_BI_Nike_CallOfModel equivalent.
     */
    interface NikeCallOfModel {
        String getCallOfModelKey();
        String getStorerKey();
        String getModelCode();
        String getModelName();
        String getCallDate();
        String getCallStatus();
        Integer getTotalStyles();
        Integer getTotalSKUs();
        BigDecimal getTotalQtyOrdered();
        BigDecimal getTotalQtyReceived();
        BigDecimal getPercentComplete();
        LocalDateTime getTargetDate();
        Integer getDaysRemaining();
        String getPriorityLevel();
        BigDecimal getCompletionRate();
    }

    /**
     * V_BI_Nike_CRW equivalent - Cross Reference Warehouse.
     */
    interface NikeCRW {
        String getCRWKey();
        String getStorerKey();
        String getSourceWarehouse();
        String getDestinationWarehouse();
        String getSku();
        String getStyleColorSize();
        BigDecimal getQtyRequested();
        BigDecimal getQtyAllocated();
        BigDecimal getQtyShipped();
        BigDecimal getQtyReceived();
        String getTransferStatus();
        LocalDateTime getRequestDate();
        LocalDateTime getShipDate();
        LocalDateTime getReceiveDate();
        Integer getTransitDays();
        String getPriority();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // H&M Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_HM_Inbound equivalent.
     */
    interface HMInbound {
        String getReceiptKey();
        String getStorerKey();
        String getArticleNumber();
        String getArticleDescription();
        String getColorCode();
        String getSizeCode();
        String getDivision();
        String getDepartment();
        String getGarmentGroup();
        BigDecimal getQtyExpected();
        BigDecimal getQtyReceived();
        String getSupplierCode();
        String getCountryOfOrigin();
        LocalDateTime getProductionWeek();
        String getQualityGrade();
        Boolean getIsQualityChecked();
        String getHangerStatus();
        BigDecimal getHangerQty();
    }

    /**
     * V_BI_HM_QualityCheck equivalent.
     */
    interface HMQualityCheck {
        String getQCKey();
        String getReceiptKey();
        String getStorerKey();
        String getArticleNumber();
        String getSampleSize();
        Integer getDefectsFound();
        String getDefectType();
        String getDefectSeverity();
        String getQCResult(); // PASS, FAIL, CONDITIONAL
        String getInspectorId();
        LocalDateTime getInspectionDate();
        String getDisposition();
        String getNotes();
        BigDecimal getAQLPercent();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Adidas Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Adidas_Inbound equivalent.
     */
    interface AdidasInbound {
        String getReceiptKey();
        String getStorerKey();
        String getArticleNumber();
        String getArticleDescription();
        String getModelNumber();
        String getColorway();
        String getSizeUS();
        String getSizeEU();
        String getGender();
        String getSportCategory();
        String getCollection();
        BigDecimal getQtyExpected();
        BigDecimal getQtyReceived();
        String getSourceFactory();
        String getCountryOfOrigin();
        LocalDateTime getManufactureDate();
        String getBatchNumber();
    }

    /**
     * V_BI_Adidas_Compliance equivalent.
     */
    interface AdidasCompliance {
        String getComplianceKey();
        String getReceiptKey();
        String getStorerKey();
        String getSku();
        String getComplianceType();
        String getComplianceStatus();
        Boolean getLabelingCompliant();
        Boolean getPackagingCompliant();
        Boolean getDocumentationCompliant();
        String getCertificationStatus();
        LocalDateTime getCheckDate();
        String getCheckedBy();
        String getDeviations();
        String getCorrectiveAction();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Other Brand Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Columbia_UCC equivalent.
     */
    interface ColumbiaUCC {
        String getUCCKey();
        String getReceiptKey();
        String getStorerKey();
        String getSku();
        String getUCCCode();
        String getUCCType();
        String getGTIN();
        String getSerialNumber();
        String getBatchLot();
        LocalDateTime getProductionDate();
        LocalDateTime getExpiryDate();
        String getCountryOfOrigin();
        LocalDateTime getStampDate();
        String getStampedBy();
        String getVerificationStatus();
    }

    /**
     * V_BI_Unilever_Inbound equivalent.
     */
    interface UnileverInbound {
        String getReceiptKey();
        String getStorerKey();
        String getMaterialCode();
        String getMaterialDescription();
        String getBrandCode();
        String getCategoryCode();
        String getSubCategoryCode();
        String getBatchNumber();
        LocalDateTime getProductionDate();
        LocalDateTime getExpiryDate();
        Integer getShelfLifeDays();
        Integer getRemainingShelfLife();
        BigDecimal getQtyReceived();
        String getPalletId();
        String getSupplierBatch();
        Boolean getIsFIFOCompliant();
    }

    /**
     * V_BI_Mondelez_Expiry equivalent.
     */
    interface MondelezExpiry {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getBatchNumber();
        LocalDateTime getProductionDate();
        LocalDateTime getExpiryDate();
        Integer getTotalShelfLifeDays();
        Integer getDaysRemaining();
        BigDecimal getPercentLifeRemaining();
        BigDecimal getQtyOnHand();
        String getExpiryBucket(); // OK, WARNING, CRITICAL, EXPIRED
        String getLocationKey();
        LocalDateTime getLastMovementDate();
        String getDispositionStatus();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Regional Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_APAC_Regional equivalent.
     */
    interface APACRegional {
        String getCountryCode();
        String getCountryName();
        String getStorerKey();
        String getStorerName();
        LocalDateTime getReportDate();
        Integer getTotalReceipts();
        BigDecimal getTotalQty();
        BigDecimal getTotalValue();
        String getCurrency();
        BigDecimal getValueUSD();
        Integer getComplianceIssues();
        Integer getCustomsHolds();
        BigDecimal getAvgProcessingHours();
        BigDecimal getOnTimePercent();
    }

    /**
     * V_BI_Thailand_Customs equivalent.
     */
    interface ThailandCustoms {
        String getCustomsKey();
        String getReceiptKey();
        String getStorerKey();
        String getImportPermitNumber();
        String getCustomsDeclarationNumber();
        String getHSCode();
        String getTariffDescription();
        BigDecimal getDutyRate();
        BigDecimal getDutyAmount();
        BigDecimal getVATAmount();
        BigDecimal getExciseTax();
        String getClearanceStatus();
        LocalDateTime getSubmissionDate();
        LocalDateTime getClearanceDate();
        String getBOIStatus();
        String getFTAScheme();
    }

    /**
     * V_BI_Taiwan_Inbound equivalent.
     */
    interface TaiwanInbound {
        String getReceiptKey();
        String getStorerKey();
        String getImportLicenseNumber();
        String getCCCCode();
        String getProductCertification();
        String getBSMINumber();
        String getOriginCertificate();
        BigDecimal getQtyImported();
        BigDecimal getDeclaredValue();
        String getCurrency();
        BigDecimal getDutyPaid();
        String getCustomsStatus();
        LocalDateTime getInspectionDate();
        String getInspectionResult();
    }

    /**
     * V_BI_India_GST equivalent.
     */
    interface IndiaGST {
        String getGSTKey();
        String getReceiptKey();
        String getStorerKey();
        String getSupplierGSTIN();
        String getReceiverGSTIN();
        String getHSNCode();
        String getProductDescription();
        BigDecimal getTaxableValue();
        BigDecimal getCGSTRate();
        BigDecimal getCGSTAmount();
        BigDecimal getSGSTRate();
        BigDecimal getSGSTAmount();
        BigDecimal getIGSTRate();
        BigDecimal getIGSTAmount();
        BigDecimal getTotalGST();
        String getInvoiceNumber();
        LocalDateTime getInvoiceDate();
        String getEWayBillNumber();
        Boolean getIsITCEligible();
    }

    /**
     * V_BI_Singapore_Compliance equivalent.
     */
    interface SingaporeCompliance {
        String getComplianceKey();
        String getReceiptKey();
        String getStorerKey();
        String getPermitNumber();
        String getPermitType();
        String getHSCode();
        String getProductDescription();
        BigDecimal getDeclaredValue();
        BigDecimal getGSTAmount();
        String getGSTStatus();
        String getTradeNetRef();
        LocalDateTime getSubmissionDate();
        LocalDateTime getApprovalDate();
        String getControlledGoodsStatus();
        String getOriginCountry();
        Boolean getIsFTAApplicable();
        String getFTAScheme();
    }
}
