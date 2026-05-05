package com.wms.po.domain.repository;

import com.wms.po.domain.entity.ReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Receipt entities
 */
@Repository
public interface ReceiptRepository extends JpaRepository<ReceiptEntity, String> {

    Optional<ReceiptEntity> findByReceiptKey(String receiptKey);

    Optional<ReceiptEntity> findByExternReceiptKey(String externReceiptKey);

    List<ReceiptEntity> findByStorerKeyAndFacility(String storerKey, String facility);

    List<ReceiptEntity> findByStorerKeyAndStatus(String storerKey, String status);

    @Modifying
    @Query("DELETE FROM ReceiptEntity r WHERE r.receiptKey = :receiptKey")
    void deleteByReceiptKey(@Param("receiptKey") String receiptKey);

    @Query("SELECT r FROM ReceiptEntity r WHERE r.storerKey = :storerKey AND r.status IN :statuses")
    List<ReceiptEntity> findByStorerKeyAndStatusIn(
        @Param("storerKey") String storerKey,
        @Param("statuses") List<String> statuses);

    boolean existsByExternReceiptKey(String externReceiptKey);
}
