package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.ProtocolGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProtocolGroupRepository extends JpaRepository<ProtocolGroupEntity, Long> {
    List<ProtocolGroupEntity> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByNameAndIdNot(String name, Long id);
}
