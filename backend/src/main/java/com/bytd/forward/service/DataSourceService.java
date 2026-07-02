package com.bytd.forward.service;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.web.dto.ConfigCarrierDto;
import com.bytd.forward.web.dto.DataSourceRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final ObjectMapper mapper;

    public DataSourceService(DataSourceRepository repository, ObjectMapper mapper) {
        this.repository = repository;
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
        return toDto(repository.save(e));
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
