package com.bytd.forward.service;

import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.ConfigCarrierDto;
import com.bytd.forward.web.dto.OutputTargetRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutputTargetService {

    private static final Logger log = LoggerFactory.getLogger(OutputTargetService.class);

    private final OutputTargetRepository repository;
    private final ProtocolRepository protocolRepository;
    private final ProtocolRuntimeManager runtimeManager;
    private final ObjectMapper mapper;

    public OutputTargetService(OutputTargetRepository repository,
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
    public ConfigCarrierDto create(OutputTargetRequest req) {
        OutputTargetEntity e = new OutputTargetEntity();
        e.setName(req.name);
        e.setType(req.type);
        e.setConfig(req.config == null ? "{}" : req.config.toString());
        return toDto(repository.save(e));
    }

    @Transactional
    public ConfigCarrierDto update(Long id, OutputTargetRequest req) {
        OutputTargetEntity e = find(id);
        if (req.name != null) e.setName(req.name);
        if (req.type != null) e.setType(req.type);
        if (req.config != null) e.setConfig(req.config.toString());
        ConfigCarrierDto dto = toDto(repository.save(e));
        reloadRunningProtocols(id);
        return dto;
    }

    /** 输出目标配置变更后重启引用它的运行中协议，使共享连接按新配置重建。 */
    private void reloadRunningProtocols(Long targetId) {
        for (ProtocolEntity p : protocolRepository.findByOutputTargetId(targetId)) {
            try {
                runtimeManager.reloadIfRunning(p.getId());
            } catch (Exception ex) {
                log.warn("输出目标[{}]变更后重启协议[{}]失败: {}", targetId, p.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private OutputTargetEntity find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("输出目标不存在: " + id));
    }

    private ConfigCarrierDto toDto(OutputTargetEntity e) {
        JsonNode config;
        try {
            config = mapper.readTree(e.getConfig() == null ? "{}" : e.getConfig());
        } catch (Exception ex) {
            config = mapper.createObjectNode();
        }
        return new ConfigCarrierDto(e.getId(), e.getName(), e.getType(), config, e.getCreatedAt(), e.getUpdatedAt());
    }
}
