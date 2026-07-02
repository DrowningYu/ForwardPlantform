package com.bytd.forward.domain.repository;

import com.bytd.forward.domain.entity.ScriptVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScriptVersionRepository extends JpaRepository<ScriptVersionEntity, Long> {
    List<ScriptVersionEntity> findByProtocolIdOrderByVersionDesc(Long protocolId);
    Optional<ScriptVersionEntity> findTopByProtocolIdOrderByVersionDesc(Long protocolId);
    void deleteByProtocolId(Long protocolId);
}
