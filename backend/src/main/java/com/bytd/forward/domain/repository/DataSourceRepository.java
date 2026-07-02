package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.DataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {
    boolean existsByName(String name);
    List<DataSourceEntity> findAllByOrderByIdAsc();
}
