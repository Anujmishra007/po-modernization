package com.wms.po.activity.impl;

import com.wms.po.activity.PersistenceActivity;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.entity.ReceiptDetailEntity;
import com.wms.po.domain.entity.ReceiptEntity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.MappingResult;
import com.wms.po.domain.repository.ReceiptDetailRepository;
import com.wms.po.domain.repository.ReceiptRepository;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of PersistenceActivity with compensation methods.
 *
 * Error codes:
 * - RCV_005 (68904) - Receipt Header Creation Failed
 * - RCV_006 (68905) - Receipt Detail Creation Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PersistenceActivityImpl implements PersistenceActivity {

    private final ReceiptRepository receiptRepository;
    private final ReceiptDetailRepository detailRepository;
    private final KeyGeneratorService keyGenerator;

    /**
     * Create receipt header.
     *
     * Error codes:
     * - RCV_005 (68904) - Receipt Header Creation Failed
     */
    @Override
    @Transactional
    public String createReceiptHeader(MappingResult mapping) {
        log.info("Creating receipt header for externKey={}", mapping.getExternReceiptKey());

        String receiptKey = null;

        try {
            receiptKey = keyGenerator.generateReceiptKey();

            ReceiptEntity receipt = ReceiptEntity.builder()
                .receiptKey(receiptKey)
                .externReceiptKey(mapping.getExternReceiptKey())
                .storerKey(mapping.getStorerKey())
                .facility(mapping.getFacility())
                .receiptType("PO")
                .status("0")  // Initial status
                .addDate(LocalDateTime.now())
                .addWho(mapping.getUserId())
                .build();

            receiptRepository.save(receipt);

            log.info("Created receipt header: receiptKey={}", receiptKey);
            return receiptKey;

        } catch (Exception e) {
            log.error("Failed to create receipt header for {}: {} (legacy error 68904)",
                mapping.getExternReceiptKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to create receipt header: " + e.getMessage(), e)
                .withDetail("externReceiptKey", mapping.getExternReceiptKey())
                .withDetail("storerKey", mapping.getStorerKey());
        }
    }

    @Override
    @Transactional
    public void deleteReceiptHeader(String receiptKey) {
        log.warn("COMPENSATION: Deleting receipt header: receiptKey={}", receiptKey);

        // Delete details first (if any remain)
        detailRepository.deleteByReceiptKey(receiptKey);

        // Delete header
        receiptRepository.deleteByReceiptKey(receiptKey);

        log.info("COMPENSATION complete: Deleted receipt header: {}", receiptKey);
    }

    @Override
    @Transactional
    public List<String> createReceiptDetails(String receiptKey, List<DetailMapping> details) {
        log.info("Creating {} receipt details for receiptKey={}", details.size(), receiptKey);

        List<String> detailKeys = new ArrayList<>();

        for (int i = 0; i < details.size(); i++) {
            DetailMapping detail = details.get(i);
            String detailKey = keyGenerator.generateReceiptDetailKey();

            ReceiptDetailEntity entity = ReceiptDetailEntity.builder()
                .receiptDetailKey(detailKey)
                .receiptKey(receiptKey)
                .lineNumber(i + 1)
                .sku(detail.getSku())
                .qtyExpected(detail.getQtyExpected())
                .uom(detail.getUom())
                .packKey(detail.getPackKey())
                .poKey(detail.getPoKey())
                .poLineNumber(detail.getPoLineNumber())
                .status("0")
                .addDate(LocalDateTime.now())
                .build();

            // Apply lottables
            if (detail.getLottables() != null) {
                applyLottables(entity, detail.getLottables());
            }

            detailRepository.save(entity);
            detailKeys.add(detailKey);
        }

        log.info("Created {} receipt details", detailKeys.size());
        return detailKeys;
    }

    @Override
    @Transactional
    public void deleteReceiptDetails(List<String> detailKeys) {
        log.warn("COMPENSATION: Deleting {} receipt details", detailKeys.size());

        detailRepository.deleteAllByReceiptDetailKeyIn(detailKeys);

        log.info("COMPENSATION complete: Deleted {} receipt details", detailKeys.size());
    }

    @Override
    @Transactional
    public void updateReceiptStatus(String receiptKey, String status) {
        log.info("Updating receipt status: receiptKey={}, status={}", receiptKey, status);

        receiptRepository.findByReceiptKey(receiptKey).ifPresent(receipt -> {
            receipt.setStatus(status);
            receipt.setEditDate(LocalDateTime.now());
            receiptRepository.save(receipt);
        });
    }

    private void applyLottables(ReceiptDetailEntity entity, Map<String, String> lottables) {
        entity.setLottable01(lottables.get("lottable01"));
        entity.setLottable02(lottables.get("lottable02"));
        entity.setLottable03(lottables.get("lottable03"));
        entity.setLottable04(lottables.get("lottable04"));
        entity.setLottable05(lottables.get("lottable05"));
        entity.setLottable06(lottables.get("lottable06"));
        entity.setLottable07(lottables.get("lottable07"));
        entity.setLottable08(lottables.get("lottable08"));
        entity.setLottable09(lottables.get("lottable09"));
        entity.setLottable10(lottables.get("lottable10"));
    }
}
