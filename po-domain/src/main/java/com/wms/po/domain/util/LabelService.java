package com.wms.po.domain.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * GS1 Label generation service.
 *
 * Replaces SQL function: fnc_GetGS1Label (123 LOC)
 *
 * Generates label data for:
 * - SSCC/UCC-128 labels
 * - Shipping labels (GS1 compliant)
 * - Pallet/Carton labels
 * - Product identification labels
 *
 * Formats data for label printing systems (Bartender, ZPL, etc.).
 */
public final class LabelService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private LabelService() {
        // Utility class - no instantiation
    }

    /**
     * Generate SSCC label data.
     * Equivalent to fnc_GetGS1Label for SSCC labels.
     *
     * @param sscc18 The 18-digit SSCC
     * @param companyName Company name
     * @param fromAddress Ship from address
     * @param toAddress Ship to address
     * @return Label data map for label printing
     */
    public static Map<String, String> generateSSCCLabel(String sscc18, String companyName,
                                                         Address fromAddress, Address toAddress) {
        Map<String, String> labelData = new HashMap<>();

        // Header section
        labelData.put("COMPANY_NAME", companyName);
        labelData.put("LABEL_TYPE", "SSCC");

        // From address
        if (fromAddress != null) {
            labelData.put("FROM_NAME", fromAddress.name());
            labelData.put("FROM_ADDR1", fromAddress.address1());
            labelData.put("FROM_ADDR2", fromAddress.address2());
            labelData.put("FROM_CITY", fromAddress.city());
            labelData.put("FROM_STATE", fromAddress.state());
            labelData.put("FROM_ZIP", fromAddress.postalCode());
            labelData.put("FROM_COUNTRY", fromAddress.country());
        }

        // To address
        if (toAddress != null) {
            labelData.put("TO_NAME", toAddress.name());
            labelData.put("TO_ADDR1", toAddress.address1());
            labelData.put("TO_ADDR2", toAddress.address2());
            labelData.put("TO_CITY", toAddress.city());
            labelData.put("TO_STATE", toAddress.state());
            labelData.put("TO_ZIP", toAddress.postalCode());
            labelData.put("TO_COUNTRY", toAddress.country());
        }

        // SSCC barcode data
        labelData.put("SSCC18", sscc18);
        labelData.put("SSCC18_FORMATTED", formatSSCC(sscc18));
        labelData.put("SSCC_BARCODE", BarcodeGenerator.generateGS1128("00" + sscc18));
        labelData.put("SSCC_HRI", "(00)" + sscc18);

        return labelData;
    }

    /**
     * Generate shipping label data (GS1 compliant).
     *
     * @param params Label parameters
     * @return Label data map
     */
    public static Map<String, String> generateShippingLabel(ShippingLabelParams params) {
        Map<String, String> labelData = new HashMap<>();

        // Header
        labelData.put("LABEL_TYPE", "SHIPPING");
        labelData.put("CARRIER", params.carrier());
        labelData.put("SERVICE", params.serviceType());

        // From/To addresses
        if (params.fromAddress() != null) {
            addAddressToLabel(labelData, "FROM", params.fromAddress());
        }
        if (params.toAddress() != null) {
            addAddressToLabel(labelData, "TO", params.toAddress());
        }

        // Shipment details
        labelData.put("WEIGHT", String.valueOf(params.weight()));
        labelData.put("WEIGHT_UOM", params.weightUom());
        labelData.put("DIMENSIONS", params.dimensions());
        labelData.put("PIECE_COUNT", String.valueOf(params.pieceCount()));

        // Reference numbers
        labelData.put("TRACKING_NUMBER", params.trackingNumber());
        labelData.put("ORDER_NUMBER", params.orderNumber());
        labelData.put("PO_NUMBER", params.poNumber());

        // GS1-128 barcodes
        BarcodeGenerator.GS1128Builder barcodeBuilder = BarcodeGenerator.gs1128();

        if (params.sscc18() != null) {
            barcodeBuilder.sscc(params.sscc18());
            labelData.put("SSCC18", params.sscc18());
            labelData.put("SSCC_HRI", "(00)" + params.sscc18());
        }

        labelData.put("GS1_BARCODE", barcodeBuilder.build());

        return labelData;
    }

    /**
     * Generate pallet label data.
     *
     * @param params Pallet label parameters
     * @return Label data map
     */
    public static Map<String, String> generatePalletLabel(PalletLabelParams params) {
        Map<String, String> labelData = new HashMap<>();

        labelData.put("LABEL_TYPE", "PALLET");
        labelData.put("PALLET_ID", params.palletId());
        labelData.put("LOCATION", params.location());
        labelData.put("TOTAL_CASES", String.valueOf(params.totalCases()));
        labelData.put("TOTAL_UNITS", String.valueOf(params.totalUnits()));
        labelData.put("TOTAL_WEIGHT", String.valueOf(params.totalWeight()));

        // SSCC
        if (params.sscc18() != null) {
            labelData.put("SSCC18", params.sscc18());
            labelData.put("SSCC_HRI", "(00)" + params.sscc18());
            labelData.put("SSCC_BARCODE_DATA", "00" + params.sscc18());
        }

        // Contents summary
        if (params.contents() != null && !params.contents().isEmpty()) {
            StringBuilder contentsSummary = new StringBuilder();
            int lineNum = 1;
            for (ContentLine line : params.contents()) {
                labelData.put("SKU_" + lineNum, line.sku());
                labelData.put("DESC_" + lineNum, line.description());
                labelData.put("QTY_" + lineNum, String.valueOf(line.quantity()));
                contentsSummary.append(line.sku()).append(": ").append(line.quantity()).append("\n");
                lineNum++;
            }
            labelData.put("CONTENTS_SUMMARY", contentsSummary.toString().trim());
            labelData.put("LINE_COUNT", String.valueOf(params.contents().size()));
        }

        // Dates
        labelData.put("PRINT_DATE", LocalDate.now().format(DATE_FORMATTER));

        return labelData;
    }

    /**
     * Generate product label data (GTIN-based).
     *
     * @param params Product label parameters
     * @return Label data map
     */
    public static Map<String, String> generateProductLabel(ProductLabelParams params) {
        Map<String, String> labelData = new HashMap<>();

        labelData.put("LABEL_TYPE", "PRODUCT");
        labelData.put("SKU", params.sku());
        labelData.put("DESCRIPTION", params.description());
        labelData.put("GTIN", params.gtin());

        // Build GS1-128 barcode
        BarcodeGenerator.GS1128Builder builder = BarcodeGenerator.gs1128();

        if (params.gtin() != null) {
            builder.gtin(params.gtin());
            labelData.put("GTIN_HRI", "(01)" + params.gtin());
        }

        if (params.batchLot() != null) {
            builder.batchLot(params.batchLot());
            labelData.put("BATCH_LOT", params.batchLot());
            labelData.put("BATCH_HRI", "(10)" + params.batchLot());
        }

        if (params.expirationDate() != null) {
            String expDate = params.expirationDate().format(DATE_FORMATTER);
            builder.expirationDate(expDate);
            labelData.put("EXPIRATION_DATE", expDate);
            labelData.put("EXP_HRI", "(17)" + expDate);
        }

        if (params.serialNumber() != null) {
            builder.serial(params.serialNumber());
            labelData.put("SERIAL_NUMBER", params.serialNumber());
            labelData.put("SERIAL_HRI", "(21)" + params.serialNumber());
        }

        String gs1Data = builder.build();
        labelData.put("GS1_BARCODE_DATA", gs1Data);
        labelData.put("GS1_HRI", BarcodeGenerator.formatHRI(gs1Data));

        return labelData;
    }

    /**
     * Generate carton label data.
     *
     * @param params Carton label parameters
     * @return Label data map
     */
    public static Map<String, String> generateCartonLabel(CartonLabelParams params) {
        Map<String, String> labelData = new HashMap<>();

        labelData.put("LABEL_TYPE", "CARTON");
        labelData.put("CARTON_ID", params.cartonId());
        labelData.put("SKU", params.sku());
        labelData.put("DESCRIPTION", params.description());
        labelData.put("QUANTITY", String.valueOf(params.quantity()));
        labelData.put("UOM", params.uom());

        // UCC/SSCC
        if (params.sscc18() != null) {
            labelData.put("SSCC18", params.sscc18());
            labelData.put("SSCC_HRI", "(00)" + params.sscc18());
        }

        // GTIN
        if (params.gtin() != null) {
            labelData.put("GTIN", params.gtin());
            labelData.put("GTIN_HRI", "(01)" + params.gtin());
        }

        // Lot/Batch
        if (params.batchLot() != null) {
            labelData.put("BATCH_LOT", params.batchLot());
        }

        // Build combined GS1-128
        BarcodeGenerator.GS1128Builder builder = BarcodeGenerator.gs1128();
        if (params.sscc18() != null) {
            builder.sscc(params.sscc18());
        }
        if (params.gtin() != null) {
            builder.gtin(params.gtin());
        }
        if (params.batchLot() != null) {
            builder.batchLot(params.batchLot());
        }
        if (params.quantity() > 0) {
            builder.quantity(params.quantity());
        }

        String gs1Data = builder.build();
        labelData.put("GS1_BARCODE_DATA", gs1Data);
        labelData.put("GS1_HRI", BarcodeGenerator.formatHRI(gs1Data));

        // Weight/dimensions
        labelData.put("GROSS_WEIGHT", String.valueOf(params.grossWeight()));
        labelData.put("NET_WEIGHT", String.valueOf(params.netWeight()));
        labelData.put("WEIGHT_UOM", params.weightUom());

        return labelData;
    }

    /**
     * Format SSCC-18 with spaces for readability.
     * Format: X XXXX XXXX XXXX XXXX X
     *
     * @param sscc18 The 18-digit SSCC
     * @return Formatted SSCC
     */
    public static String formatSSCC(String sscc18) {
        if (sscc18 == null || sscc18.length() != 18) {
            return sscc18;
        }
        return sscc18.charAt(0) + " " +
               sscc18.substring(1, 5) + " " +
               sscc18.substring(5, 9) + " " +
               sscc18.substring(9, 13) + " " +
               sscc18.substring(13, 17) + " " +
               sscc18.charAt(17);
    }

    /**
     * Format GTIN-14 with spaces for readability.
     *
     * @param gtin14 The 14-digit GTIN
     * @return Formatted GTIN
     */
    public static String formatGTIN14(String gtin14) {
        if (gtin14 == null || gtin14.length() != 14) {
            return gtin14;
        }
        return gtin14.charAt(0) + " " +
               gtin14.substring(1, 3) + " " +
               gtin14.substring(3, 8) + " " +
               gtin14.substring(8, 13) + " " +
               gtin14.charAt(13);
    }

    /**
     * Generate ZPL code for label (Zebra printers).
     *
     * @param labelData Label data map
     * @param template ZPL template name
     * @return ZPL code string
     */
    public static String generateZPL(Map<String, String> labelData, String template) {
        StringBuilder zpl = new StringBuilder();
        zpl.append("^XA\n"); // Start format

        // Basic label with SSCC
        if (labelData.containsKey("SSCC18")) {
            zpl.append("^FO50,50^A0N,30,30^FD").append(labelData.get("COMPANY_NAME")).append("^FS\n");
            zpl.append("^FO50,100^BY3^BCN,100,Y,N,N^FD").append(labelData.get("GS1_BARCODE_DATA")).append("^FS\n");
            zpl.append("^FO50,220^A0N,25,25^FD").append(labelData.get("SSCC_HRI")).append("^FS\n");
        }

        zpl.append("^XZ\n"); // End format
        return zpl.toString();
    }

    private static void addAddressToLabel(Map<String, String> labelData, String prefix, Address address) {
        labelData.put(prefix + "_NAME", address.name());
        labelData.put(prefix + "_ADDR1", address.address1());
        labelData.put(prefix + "_ADDR2", address.address2() != null ? address.address2() : "");
        labelData.put(prefix + "_CITY", address.city());
        labelData.put(prefix + "_STATE", address.state());
        labelData.put(prefix + "_ZIP", address.postalCode());
        labelData.put(prefix + "_COUNTRY", address.country());
        labelData.put(prefix + "_FULL", formatFullAddress(address));
    }

    private static String formatFullAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.name()).append("\n");
        sb.append(address.address1());
        if (address.address2() != null && !address.address2().isBlank()) {
            sb.append("\n").append(address.address2());
        }
        sb.append("\n").append(address.city()).append(", ").append(address.state());
        sb.append(" ").append(address.postalCode());
        sb.append("\n").append(address.country());
        return sb.toString();
    }

    // Record types for label parameters

    public record Address(
        String name,
        String address1,
        String address2,
        String city,
        String state,
        String postalCode,
        String country
    ) {}

    public record ShippingLabelParams(
        String carrier,
        String serviceType,
        Address fromAddress,
        Address toAddress,
        double weight,
        String weightUom,
        String dimensions,
        int pieceCount,
        String trackingNumber,
        String orderNumber,
        String poNumber,
        String sscc18
    ) {}

    public record PalletLabelParams(
        String palletId,
        String sscc18,
        String location,
        int totalCases,
        int totalUnits,
        double totalWeight,
        java.util.List<ContentLine> contents
    ) {}

    public record ContentLine(
        String sku,
        String description,
        int quantity
    ) {}

    public record ProductLabelParams(
        String sku,
        String description,
        String gtin,
        String batchLot,
        LocalDate expirationDate,
        String serialNumber
    ) {}

    public record CartonLabelParams(
        String cartonId,
        String sscc18,
        String sku,
        String description,
        String gtin,
        int quantity,
        String uom,
        String batchLot,
        double grossWeight,
        double netWeight,
        String weightUom
    ) {}
}
