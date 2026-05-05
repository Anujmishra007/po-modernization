package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryPostingActivity;
import com.wms.po.domain.service.FinalizeReceiptService;
import com.wms.po.domain.service.InventoryPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * In production WMS, LOTxLOCxID is the master inventory table:
 * - LOT: Lottable attributes (lot number, expiry, color, size, etc.)
 * - LOC: Physical warehouse location
 * - ID: License plate number (container/pallet identifier)
 */
@Component
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
                log.warn("No lines to post for receipt {}", request.getReceiptKey());
                return PostingResult.builder()
                    .success(true)
                    .inventoryIds(inventoryIds)
                    .recordsCreated(0)
                    .totalQuantity(BigDecimal.ZERO)
                    .warnings(List.of("No lines with positive quantity to post"))
                    .build();
            }

            // Convert to service posting objects
            List<InventoryPostingService.InventoryPosting> postings = linesToPost.stream()
                .map(line -> convertToServicePosting(request, line))
                .collect(Collectors.toList());

            // Post in batch
            inventoryIds = inventoryPostingService.postInventoryBatch(postings);

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

        } catch (Exception e) {
            log.error("Inventory posting failed: {}", e.getMessage(), e);
            return PostingResult.failed("Inventory posting failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteInventory(List<String> inventoryIds) {
        log.warn("COMPENSATION: Deleting {} inventory records", inventoryIds.size());

        int deletedCount = inventoryPostingService.deleteInventoryBatch(
            inventoryIds,
            "SYSTEM",
            "Finalization rollback"
        );

        log.info("COMPENSATION complete: Deleted {}/{} inventory records", deletedCount, inventoryIds.size());
    }

    @Override
    @Transactional
    public void adjustInventory(String inventoryId, BigDecimal adjustment, String reason, String userId) {
        log.info("Adjusting inventory {}: qty={}, reason={}", inventoryId, adjustment, reason);

        boolean adjusted = inventoryPostingService.adjustInventory(inventoryId, adjustment, reason, userId);

        if (!adjusted) {
            throw new RuntimeException("Failed to adjust inventory " + inventoryId);
        }
    }

    /**
     * Build line postings from receipt details.
     */
    private List<LinePosting> buildLinesFromReceipt(String receiptKey, String storerKey) {
        List<FinalizeReceiptService.ReceiptDetailRecord> details =
            finalizeReceiptService.getReceiptDetails(receiptKey);

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
