package com.bytd.forward.service;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.runtime.sink.config.MqttSinkConfig;
import com.bytd.forward.runtime.source.config.MqttSourceConfig;
import com.bytd.forward.web.dto.BindingCheckResultDto;
import com.bytd.forward.web.dto.BindingWarningDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProtocolBindingWarningService {

    private final ProtocolRepository protocolRepository;
    private final DataSourceRepository dataSourceRepository;
    private final OutputTargetRepository outputTargetRepository;
    private final ProtocolRuntimeManager runtimeManager;
    private final ObjectMapper mapper;

    public ProtocolBindingWarningService(ProtocolRepository protocolRepository,
                                         DataSourceRepository dataSourceRepository,
                                         OutputTargetRepository outputTargetRepository,
                                         @Lazy ProtocolRuntimeManager runtimeManager,
                                         ObjectMapper mapper) {
        this.protocolRepository = protocolRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.outputTargetRepository = outputTargetRepository;
        this.runtimeManager = runtimeManager;
        this.mapper = mapper;
    }

    public BindingCheckResultDto analyze(Long protocolId, Long sourceId, Long outputTargetId,
                                         StartCheckMode mode) {
        List<BindingWarningDto> blockers = new ArrayList<>();
        List<BindingWarningDto> warnings = new ArrayList<>();

        if (sourceId != null) {
            dataSourceRepository.findById(sourceId).ifPresent(ds -> {
                BindingWarningDto w = analyzeSource(ds, protocolId, mode);
                if (w != null) {
                    if ("BLOCK".equals(w.level())) {
                        blockers.add(w);
                    } else {
                        warnings.add(w);
                    }
                }
            });
        }

        if (outputTargetId != null) {
            outputTargetRepository.findById(outputTargetId).ifPresent(ot -> {
                BindingWarningDto w = analyzeSink(ot, protocolId, mode);
                if (w != null) {
                    if ("BLOCK".equals(w.level())) {
                        blockers.add(w);
                    } else {
                        warnings.add(w);
                    }
                }
            });
        }

        return new BindingCheckResultDto(List.copyOf(blockers), List.copyOf(warnings));
    }

    public BindingCheckResultDto analyzeForStart(Long protocolId) {
        ProtocolEntity p = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + protocolId));
        return analyze(protocolId, p.getSourceId(), p.getOutputTargetId(), StartCheckMode.START);
    }

    public void assertCanStart(Long protocolId, boolean acknowledgeWarnings) {
        BindingCheckResultDto result = analyzeForStart(protocolId);
        if (!result.blockers().isEmpty()) {
            throw new ProtocolBindingBlockedException(result);
        }
        if (!result.warnings().isEmpty() && !acknowledgeWarnings) {
            throw new ProtocolBindingWarningException(result);
        }
    }

    private BindingWarningDto analyzeSource(DataSourceEntity ds, Long excludeProtocolId, StartCheckMode mode) {
        List<ProtocolEntity> peers = findPeers(ds.getId(), null, excludeProtocolId);
        List<String> names = filterPeerNames(peers, mode);
        if (names.isEmpty()) {
            return null;
        }

        String type = normalizeType(ds.getType());
        String related = formatRelated(names);

        if ("MQTT".equals(type)) {
            boolean fixedClientId = hasMqttClientId(ds.getConfig(), true);
            if (fixedClientId) {
                return new BindingWarningDto(
                        "MQTT_SOURCE_CLIENT_ID",
                        "BLOCK",
                        "SOURCE",
                        ds.getName(),
                        "数据源「" + ds.getName() + "」已配置固定 Client ID，"
                                + related
                                + "。多协议同时运行时会共用同一 Client ID，MQTT Broker 会踢掉旧连接，导致协议互相抢夺连接、间歇断连。",
                        names
                );
            }
            return new BindingWarningDto(
                    "MQTT_SOURCE_AUTO_CLIENT_ID",
                    "WARN",
                    "SOURCE",
                    ds.getName(),
                    "数据源「" + ds.getName() + "」未配置 Client ID，"
                            + related
                            + "。平台会为每个协议自动生成唯一 Client ID，不会互斥，但会建立多条独立连接与订阅。",
                    names
            );
        }

        if ("KAFKA".equals(type)) {
            return new BindingWarningDto(
                    "KAFKA_SOURCE_SHARED_GROUP",
                    "WARN",
                    "SOURCE",
                    ds.getName(),
                    "数据源「" + ds.getName() + "」为 Kafka，"
                            + related
                            + "。多协议同时运行且共用同一消费组 groupId 时，Kafka 会在 Consumer 之间分摊分区，每条消息通常只进入一个协议，并非全量广播。",
                    names
            );
        }

        return null;
    }

    private BindingWarningDto analyzeSink(OutputTargetEntity ot, Long excludeProtocolId, StartCheckMode mode) {
        List<ProtocolEntity> peers = findPeers(null, ot.getId(), excludeProtocolId);
        List<String> names = filterPeerNames(peers, mode);
        if (names.isEmpty()) {
            return null;
        }

        String type = normalizeType(ot.getType());
        String related = formatRelated(names);

        if ("MQTT".equals(type)) {
            boolean fixedClientId = hasMqttClientId(ot.getConfig(), false);
            if (fixedClientId) {
                return new BindingWarningDto(
                        "MQTT_SINK_CLIENT_ID",
                        "BLOCK",
                        "SINK",
                        ot.getName(),
                        "输出目标「" + ot.getName() + "」已配置固定 Client ID，"
                                + related
                                + "。多协议同时运行时会共用同一 Client ID，MQTT Broker 会踢掉旧连接，导致发布端互相抢夺连接。",
                        names
                );
            }
            return new BindingWarningDto(
                    "MQTT_SINK_AUTO_CLIENT_ID",
                    "WARN",
                    "SINK",
                    ot.getName(),
                    "输出目标「" + ot.getName() + "」未配置 Client ID，"
                            + related
                            + "。平台会为每个协议自动生成唯一 Client ID，不会互斥；若多协议同时 output()，下游 topic 会收到多份消息。",
                    names
            );
        }

        if ("KAFKA".equals(type)) {
            return new BindingWarningDto(
                    "KAFKA_SINK_SHARED",
                    "WARN",
                    "SINK",
                    ot.getName(),
                    "输出目标「" + ot.getName() + "」为 Kafka，"
                            + related
                            + "。无连接互斥问题；若多协议同时 output()，会向同一 topic 重复写入消息。",
                    names
            );
        }

        if ("HTTP".equals(type)) {
            return new BindingWarningDto(
                    "HTTP_SINK_SHARED",
                    "WARN",
                    "SINK",
                    ot.getName(),
                    "输出目标「" + ot.getName() + "」为 HTTP，"
                            + related
                            + "。无连接互斥问题；若多协议同时 output()，会对同一 URL 重复发起 HTTP 请求，请注意下游负载与限流。",
                    names
            );
        }

        return null;
    }

    private List<ProtocolEntity> findPeers(Long sourceId, Long outputTargetId, Long excludeProtocolId) {
        List<ProtocolEntity> peers;
        if (sourceId != null) {
            peers = excludeProtocolId == null
                    ? protocolRepository.findBySourceId(sourceId)
                    : protocolRepository.findBySourceIdAndIdNot(sourceId, excludeProtocolId);
        } else if (outputTargetId != null) {
            peers = excludeProtocolId == null
                    ? protocolRepository.findByOutputTargetId(outputTargetId)
                    : protocolRepository.findByOutputTargetIdAndIdNot(outputTargetId, excludeProtocolId);
        } else {
            peers = List.of();
        }
        return peers;
    }

    private List<String> filterPeerNames(List<ProtocolEntity> peers, StartCheckMode mode) {
        return peers.stream()
                .filter(p -> mode == StartCheckMode.CONFIG || runtimeManager.isRunning(p.getId()))
                .map(ProtocolEntity::getName)
                .collect(Collectors.toList());
    }

    private static String formatRelated(List<String> names) {
        return "已被协议：" + String.join("、", names) + " 使用";
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.toUpperCase();
    }

    private boolean hasMqttClientId(String configJson, boolean source) {
        try {
            if (source) {
                MqttSourceConfig cfg = mapper.readValue(configJson == null ? "{}" : configJson, MqttSourceConfig.class);
                return cfg.clientId != null && !cfg.clientId.isBlank();
            }
            MqttSinkConfig cfg = mapper.readValue(configJson == null ? "{}" : configJson, MqttSinkConfig.class);
            return cfg.clientId != null && !cfg.clientId.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
