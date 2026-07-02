package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.OutputTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutputTargetRepository extends JpaRepository<OutputTargetEntity, Long> {
    boolean existsByName(String name);
    List<OutputTargetEntity> findAllByOrderByIdAsc();
}
