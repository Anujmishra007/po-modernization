package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryPostingActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.service.FinalizeReceiptService;
import com.wms.po.domain.service.InventoryPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of InventoryPostingActivity.
 * Posts inventory to the central LOTxLOCxID table.
 *
 * Maps to legacy SP:
 * - SP-060: ispFinalizeReceipt (lines 500-800 for inventory posting)
 *
 * Error codes:
 * - INV_007 (68706) - Inventory Posting Failed
 * - RCV_021 (68921) - Finalize Inventory Post Failed
 * - RCV_004 (68903) - Receipt Detail Not Found
 *
 * In production WMS, LOTxLOCxID is the master inventory table:
 * - LOT: Lottable attributes (lot number, expiry, color, size, etc.)
 * - LOC: Physical warehouse location
 * - ID: License plate number (container/pallet identifier)
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class InventoryPostingActivityImpl implements InventoryPostingActivity {

    private final InventoryPostingService inventoryPostingService;
    private final FinalizeReceiptService finalizeReceiptService;

    @Override
    @Transactional
    public PostingResult postInventory(PostingRequest request) {
        log.info("Posting inventory for receipt {}: {} lines",
            request.getReceiptKey(),
            request.getLines() != null ? request.getLines().size() : "from receipt");

        List<String> inventoryIds = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();

        try {
            // Get receipt details if lines not provided
            List<LinePosting> linesToPost = request.getLines();
            if (linesToPost == null || linesToPost.isEmpty()) {
                // Build from receipt details
                linesToPost = buildLinesFromReceipt(request.getReceiptKey(), request.getStorerKey());
            }

            if (linesToPost.isEmpty()) {
                log.warn("No lines to post for receipt {} (legacy warning)", request.getReceiptKey());
                return PostingResult.builder()
                    .success(true)
                    .inventoryIds(inventoryIds)
                    .recordsCreated(0)
                    .totalQuantity(BigDecimal.ZERO)
                    .warnings(List.of("No lines with positive quantity to post"))
                    .build();
            }

            // Validate each line before posting
            for (LinePosting line : linesToPost) {
                validateLineForPosting(request.getReceiptKey(), line);
            }

            // Convert to service posting objects
            List<InventoryPostingService.InventoryPosting> postings = linesToPost.stream()
                .map(line -> convertToServicePosting(request, line))
                .collect(Collectors.toList());

            // Post in batch
            try {
                inventoryIds = inventoryPostingService.postInventoryBatch(postings);
            } catch (Exception e) {
                log.error("Inventory batch posting failed for receipt {}: {} (legacy error 68921)",
                    request.getReceiptKey(), e.getMessage(), e);
                throw BusinessException.finalizeInventoryPostFailed(request.getReceiptKey(), e);
            }

            // Calculate total quantity
            totalQuantity = linesToPost.stream()
                .map(LinePosting::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("Inventory posted: {} records, total qty={}", inventoryIds.size(), totalQuantity);

            return PostingResult.builder()
                .success(true)
                .inventoryIds(inventoryIds)
                .recordsCreated(inventoryIds.size())
                .totalQuantity(totalQuantity)
                .warnings(warnings)
                .build();

        } catch (BusinessException e) {
            // Re-throw BusinessExceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Inventory posting failed for receipt {}: {} (legacy error 68706)",
                request.getReceiptKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INVENTORY_POSTING_FAILED,
                "Inventory posting failed for receipt: " + request.getReceiptKey(), e)
                .withDetail("receiptKey", request.getReceiptKey());
        }
    }

    /**
     * Validate line before posting.
     * Error codes:
     * - VAL_007 (69106) - Invalid Quantity
     * - VAL_008 (69107) - Quantity Cannot Be Zero
     */
    private void validateLineForPosting(String receiptKey, LinePosting line) {
        if (line.getQuantity() == null) {
            log.error("Line {} has null quantity - legacy error 69106", line.getLineNumber());
            throw new BusinessException(ErrorCode.VALIDATION_QUANTITY_INVALID,
                String.format("Invalid quantity for receipt %s line %s", receiptKey, line.getLineNumber()))
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", line.getLineNumber());
        }

        if (line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Line {} has zero/negative quantity: {} - legacy error 69107",
                line.getLineNumber(), line.getQuantity());
            throw new BusinessException(ErrorCode.VALIDATION_QUANTITY_ZERO,
                String.format("Quantity cannot be zero for receipt %s line %s", receiptKey, line.getLineNumber()))
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", line.getLineNumber())
                .withDetail("quantity", line.getQuantity());
        }

        if (line.getSku() == null || line.getSku().isBlank()) {
            log.error("Line {} has missing SKU - legacy error 68813", line.getLineNumber());
            throw BusinessException.skuNotFound("null");
        }
    }

    @Override
    @Transactional
    public void deleteInventory(List<String> inventoryIds) {
        log.warn("COMPENSATION: Deleting {} inventory records", inventoryIds.size());

        try {
            int deletedCount = inventoryPostingService.deleteInventoryBatch(
                inventoryIds,
                "SYSTEM",
                "Finalization rollback"
            );

            log.info("COMPENSATION complete: Deleted {}/{} inventory records", deletedCount, inventoryIds.size());
        } catch (Exception e) {
            log.error("COMPENSATION failed: Could not delete inventory records: {}", e.getMessage(), e);
            // Don't throw - compensation should try to complete even if partial
        }
    }

    @Override
    @Transactional
    public void adjustInventory(String inventoryId, BigDecimal adjustment, String reason, String userId) {
        log.info("Adjusting inventory {}: qty={}, reason={}", inventoryId, adjustment, reason);

        try {
            boolean adjusted = inventoryPostingService.adjustInventory(inventoryId, adjustment, reason, userId);

            if (!adjusted) {
                log.error("Inventory adjustment failed for {}: not found - legacy error 68700", inventoryId);
                throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND,
                    "Inventory record not found: " + inventoryId)
                    .withDetail("inventoryId", inventoryId);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Inventory adjustment failed for {}: {} - legacy error 68704",
                inventoryId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_FAILED,
                "Failed to adjust inventory " + inventoryId, e)
                .withDetail("inventoryId", inventoryId)
                .withDetail("adjustment", adjustment);
        }
    }

    /**
     * Build line postings from receipt details.
     * Error codes:
     * - RCV_004 (68903) - Receipt Detail Not Found
     */
    private List<LinePosting> buildLinesFromReceipt(String receiptKey, String storerKey) {
        List<FinalizeReceiptService.ReceiptDetailRecord> details;

        try {
            details = finalizeReceiptService.getReceiptDetails(receiptKey);
        } catch (Exception e) {
            log.error("Failed to get receipt details for {}: {} - legacy error 68903",
                receiptKey, e.getMessage(), e);
            throw BusinessException.receiptDetailNotFound(receiptKey, "all");
        }

        if (details == null || details.isEmpty()) {
            log.warn("No receipt details found for {} - legacy error 68903", receiptKey);
            throw BusinessException.receiptDetailNotFound(receiptKey, "all");
        }

        List<LinePosting> lines = new ArrayList<>();
        for (FinalizeReceiptService.ReceiptDetailRecord detail : details) {
            BigDecimal qtyToPost = detail.qtyReceived() != null ? detail.qtyReceived() : detail.qtyExpected();
            if (qtyToPost != null && qtyToPost.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(LinePosting.builder()
                    .lineNumber(detail.lineNumber())
                    .sku(detail.sku())
                    .quantity(qtyToPost)
                    .packKey(detail.packKey())
                    .uom(detail.uom())
                    .location(detail.location() != null ? detail.location() : "RECV")
                    .licensePlate(detail.licensePlate() != null ? detail.licensePlate() : generateLicensePlate())
                    .lottable01(detail.lottable01())
                    .lottable02(detail.lottable02())
                    .lottable03(detail.lottable03())
                    .lottable04(detail.lottable04())
                    .lottable05(detail.lottable05())
                    .lottable06(detail.lottable06())
                    .lottable07(detail.lottable07())
                    .lottable08(detail.lottable08())
                    .lottable09(detail.lottable09())
                    .lottable10(detail.lottable10())
                    .build());
            }
        }
        return lines;
    }

    /**
     * Convert activity line posting to service posting.
     */
    private InventoryPostingService.InventoryPosting convertToServicePosting(
            PostingRequest request, LinePosting line) {

        return InventoryPostingService.InventoryPosting.builder()
            .storerKey(request.getStorerKey())
            .sku(line.getSku())
            .quantity(line.getQuantity())
            .packKey(line.getPackKey())
            .uom(line.getUom())
            .location(line.getLocation() != null ? line.getLocation() : "RECV")
            .licensePlate(line.getLicensePlate() != null ? line.getLicensePlate() : generateLicensePlate())
            .receiptKey(request.getReceiptKey())
            .lineNumber(line.getLineNumber())
            .userId(request.getUserId())
            .lottable01(line.getLottable01())
            .lottable02(line.getLottable02())
            .lottable03(line.getLottable03())
            .lottable04(line.getLottable04())
            .lottable05(line.getLottable05())
            .lottable06(line.getLottable06())
            .lottable07(line.getLottable07())
            .lottable08(line.getLottable08())
            .lottable09(line.getLottable09())
            .lottable10(line.getLottable10())
            .build();
    }

    /**
     * Generate a license plate number if not provided.
     */
    private String generateLicensePlate() {
        return "LP" + System.currentTimeMillis() + String.format("%04d", (int)(Math.random() * 10000));
    }
}
