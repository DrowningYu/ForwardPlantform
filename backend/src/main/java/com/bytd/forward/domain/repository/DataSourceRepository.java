package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.DataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {
    boolean existsByName(String name);
}
