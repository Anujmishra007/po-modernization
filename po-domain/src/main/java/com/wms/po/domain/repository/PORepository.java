package com.wms.po.domain.repository;

import com.wms.po.domain.entity.POEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PO entities
 */
@Repository
public interface PORepository extends JpaRepository<POEntity, String> {

    Optional<POEntity> findByPoKey(String poKey);

    List<POEntity> findByPoKeyIn(List<String> poKeys);

    List<POEntity> findByStorerKeyAndFacility(String storerKey, String facility);

    List<POEntity> findByStorerKeyAndStatus(String storerKey, String status);

    @Query("SELECT p FROM POEntity p WHERE p.poKey IN :poKeys AND p.status = :status")
    List<POEntity> findByPoKeyInAndStatus(
        @Param("poKeys") List<String> poKeys,
        @Param("status") String status);

    @Query("SELECT p FROM POEntity p WHERE p.storerKey = :storerKey AND p.status IN ('0', '1', '2')")
    List<POEntity> findOpenPOsByStorerKey(@Param("storerKey") String storerKey);

    boolean existsByExternPoKey(String externPoKey);
}
