package com.bytd.forward.service;

import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.web.dto.ConfigCarrierDto;
import com.bytd.forward.web.dto.OutputTargetRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutputTargetService {

    private final OutputTargetRepository repository;
    private final ObjectMapper mapper;

    public OutputTargetService(OutputTargetRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ConfigCarrierDto> list() {
        return repository.findAll().stream().map(this::toDto).toList();
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
        return toDto(repository.save(e));
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
