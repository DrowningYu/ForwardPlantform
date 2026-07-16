package com.bytd.forward.service;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.BindingCheckResultDto;
import com.bytd.forward.web.dto.BindingWarningDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 协议绑定提示。
 *
 * <p>共享连接架构下，数据源/输出目标由平台统一建连并复制分发，
 * 多协议复用同一资源不再产生连接互斥，因此不存在 BLOCK 级规则，
 * 仅保留说明共享语义的 WARN 提示。</p>
 */
@Service
public class ProtocolBindingWarningService {

    private final ProtocolRepository protocolRepository;
    private final DataSourceRepository dataSourceRepository;
    private final OutputTargetRepository outputTargetRepository;
    private final ProtocolRuntimeManager runtimeManager;

    public ProtocolBindingWarningService(ProtocolRepository protocolRepository,
                                         DataSourceRepository dataSourceRepository,
                                         OutputTargetRepository outputTargetRepository,
                                         @Lazy ProtocolRuntimeManager runtimeManager) {
        this.protocolRepository = protocolRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.outputTargetRepository = outputTargetRepository;
        this.runtimeManager = runtimeManager;
    }

    public BindingCheckResultDto analyze(Long protocolId, Long sourceId, Long outputTargetId,
                                         StartCheckMode mode) {
        List<BindingWarningDto> blockers = new ArrayList<>();
        List<BindingWarningDto> warnings = new ArrayList<>();

        if (sourceId != null) {
            dataSourceRepository.findById(sourceId).ifPresent(ds -> {
                BindingWarningDto w = analyzeSource(ds, protocolId, mode);
                if (w != null) {
                    warnings.add(w);
                }
            });
        }

        if (outputTargetId != null) {
            outputTargetRepository.findById(outputTargetId).ifPresent(ot -> {
                BindingWarningDto w = analyzeSink(ot, protocolId, mode);
                if (w != null) {
                    warnings.add(w);
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
        return new BindingWarningDto(
                "SHARED_SOURCE",
                "WARN",
                "SOURCE",
                ds.getName(),
                "数据源「" + ds.getName() + "」" + formatRelated(names)
                        + "。平台使用单一共享连接接收数据，并按各协议配置的 Topic 过滤复制分发，"
                        + "不会产生连接互斥。请确认本协议的「订阅 Topic」配置符合预期"
                        + "（留空 = 接收该数据源的全部数据）。",
                names
        );
    }

    private BindingWarningDto analyzeSink(OutputTargetEntity ot, Long excludeProtocolId, StartCheckMode mode) {
        List<ProtocolEntity> peers = findPeers(null, ot.getId(), excludeProtocolId);
        List<String> names = filterPeerNames(peers, mode);
        if (names.isEmpty()) {
            return null;
        }
        return new BindingWarningDto(
                "SHARED_SINK",
                "WARN",
                "SINK",
                ot.getName(),
                "输出目标「" + ot.getName() + "」" + formatRelated(names)
                        + "。平台复用同一连接发送，不会产生连接互斥；"
                        + "多个协议同时 output() 时，下游会分别收到各协议的消息，请注意去重与负载。",
                names
        );
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
}
