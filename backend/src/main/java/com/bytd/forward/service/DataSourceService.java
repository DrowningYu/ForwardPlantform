package com.bytd.forward.service;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.ConfigCarrierDto;
import com.bytd.forward.web.dto.DataSourceRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSourceRepository repository;
    private final ProtocolRepository protocolRepository;
    private final ProtocolRuntimeManager runtimeManager;
    private final ObjectMapper mapper;

    public DataSourceService(DataSourceRepository repository,
                             ProtocolRepository protocolRepository,
                             @Lazy ProtocolRuntimeManager runtimeManager,
                             ObjectMapper mapper) {
        this.repository = repository;
        this.protocolRepository = protocolRepository;
        this.runtimeManager = runtimeManager;
        this.mapper = mapper;
    }

    public List<ConfigCarrierDto> list() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public ConfigCarrierDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public ConfigCarrierDto create(DataSourceRequest req) {
        DataSourceEntity e = new DataSourceEntity();
        e.setName(req.name);
        e.setType(req.type);
        e.setConfig(configToString(req.config));
        return toDto(repository.save(e));
    }

    @Transactional
    public ConfigCarrierDto update(Long id, DataSourceRequest req) {
        DataSourceEntity e = find(id);
        if (req.name != null) e.setName(req.name);
        if (req.type != null) e.setType(req.type);
        if (req.config != null) e.setConfig(configToString(req.config));
        ConfigCarrierDto dto = toDto(repository.save(e));
        reloadRunningProtocols(id);
        return dto;
    }

    /** 数据源配置变更后重启引用它的运行中协议，使共享连接按新配置重建。 */
    private void reloadRunningProtocols(Long dataSourceId) {
        for (ProtocolEntity p : protocolRepository.findBySourceId(dataSourceId)) {
            try {
                runtimeManager.reloadIfRunning(p.getId());
            } catch (Exception ex) {
                log.warn("数据源[{}]变更后重启协议[{}]失败: {}", dataSourceId, p.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private DataSourceEntity find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
    }

    private String configToString(JsonNode node) {
        return node == null ? "{}" : node.toString();
    }

    private ConfigCarrierDto toDto(DataSourceEntity e) {
        JsonNode config;
        try {
            config = mapper.readTree(e.getConfig() == null ? "{}" : e.getConfig());
        } catch (Exception ex) {
            config = mapper.createObjectNode();
        }
        return new ConfigCarrierDto(e.getId(), e.getName(), e.getType(), config, e.getCreatedAt(), e.getUpdatedAt());
    }
}
