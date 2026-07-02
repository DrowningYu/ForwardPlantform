package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.ProtocolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProtocolRepository extends JpaRepository<ProtocolEntity, Long> {
    boolean existsByName(String name);
    List<ProtocolEntity> findByEnabledTrue();
    List<ProtocolEntity> findAllByOrderByIdAsc();
    List<ProtocolEntity> findBySourceId(Long sourceId);
    List<ProtocolEntity> findByOutputTargetId(Long outputTargetId);
    List<ProtocolEntity> findBySourceIdAndIdNot(Long sourceId, Long id);
    List<ProtocolEntity> findByOutputTargetIdAndIdNot(Long outputTargetId, Long id);
}
