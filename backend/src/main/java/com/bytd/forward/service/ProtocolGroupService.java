package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolGroupEntity;
import com.bytd.forward.domain.repository.ProtocolGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProtocolGroupService {
    private final ProtocolGroupRepository repository;

    public ProtocolGroupService(ProtocolGroupRepository repository) { this.repository = repository; }

    public List<ProtocolGroupEntity> list() { return repository.findAllByOrderBySortOrderAscIdAsc(); }

    @Transactional
    public ProtocolGroupEntity save(Long id, String name, Integer sortOrder) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("分组名称须为 1～128 个字符");
        }
        if (repository.existsByNameAndIdNot(normalized, id == null ? -1L : id)) {
            throw new IllegalStateException("分组名称已存在");
        }
        ProtocolGroupEntity group = id == null ? new ProtocolGroupEntity() : find(id);
        group.setName(normalized);
        if (sortOrder != null) group.setSortOrder(sortOrder);
        return repository.save(group);
    }

    @Transactional
    public void delete(Long id) {
        // 外键 ON DELETE SET NULL 仅解除分类，不经过协议运行管理器。
        repository.delete(find(id));
        repository.flush();
    }

    private ProtocolGroupEntity find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("分组不存在: " + id));
    }
}
