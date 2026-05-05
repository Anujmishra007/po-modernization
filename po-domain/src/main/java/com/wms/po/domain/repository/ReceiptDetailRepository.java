package com.wms.po.domain.repository;

import com.wms.po.domain.entity.ReceiptDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ReceiptDetail entities
 */
@Repository
public interface ReceiptDetailRepository extends JpaRepository<ReceiptDetailEntity, String> {

    List<ReceiptDetailEntity> findByReceiptKey(String receiptKey);

    List<ReceiptDetailEntity> findByReceiptKeyOrderByLineNumber(String receiptKey);

    @Modifying
    @Query("DELETE FROM ReceiptDetailEntity rd WHERE rd.receiptKey = :receiptKey")
    void deleteByReceiptKey(@Param("receiptKey") String receiptKey);

    @Modifying
    @Query("DELETE FROM ReceiptDetailEntity rd WHERE rd.receiptDetailKey IN :detailKeys")
    void deleteAllByReceiptDetailKeyIn(@Param("detailKeys") List<String> detailKeys);

    @Query("SELECT rd FROM ReceiptDetailEntity rd WHERE rd.receiptKey = :receiptKey AND rd.status = :status")
    List<ReceiptDetailEntity> findByReceiptKeyAndStatus(
        @Param("receiptKey") String receiptKey,
        @Param("status") String status);

    @Query("SELECT COUNT(rd) FROM ReceiptDetailEntity rd WHERE rd.receiptKey = :receiptKey")
    long countByReceiptKey(@Param("receiptKey") String receiptKey);
}
