package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.ScriptVersionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScriptService {

    private final ScriptVersionRepository versionRepository;
    private final ProtocolRepository protocolRepository;
    private final ScriptEngineService engine;
    private final ProtocolRuntimeManager runtimeManager;

    public ScriptService(ScriptVersionRepository versionRepository,
                         ProtocolRepository protocolRepository,
                         ScriptEngineService engine,
                         ProtocolRuntimeManager runtimeManager) {
        this.versionRepository = versionRepository;
        this.protocolRepository = protocolRepository;
        this.engine = engine;
        this.runtimeManager = runtimeManager;
    }

    public List<ScriptVersionDto> listVersions(Long protocolId) {
        return versionRepository.findByProtocolIdOrderByVersionDesc(protocolId).stream()
                .map(v -> toDto(v, false))
                .toList();
    }

    public ScriptVersionDto getVersion(Long versionId) {
        ScriptVersionEntity v = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("脚本版本不存在: " + versionId));
        return toDto(v, true);
    }

    /** 获取协议当前生效的脚本（含代码），无则返回 null。 */
    public ScriptVersionDto getCurrent(Long protocolId) {
        ProtocolEntity p = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + protocolId));
        if (p.getCurrentVersionId() == null) {
            return null;
        }
        return versionRepository.findById(p.getCurrentVersionId())
                .map(v -> toDto(v, true))
                .orElse(null);
    }

    /** 保存为新版本：编译校验后作为当前版本；若协议在运行则热更新（重启应用）。 */
    @Transactional
    public ScriptVersionDto saveNewVersion(Long protocolId, String code, String remark) {
        ProtocolEntity p = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + protocolId));

        int nextVersion = versionRepository.findTopByProtocolIdOrderByVersionDesc(protocolId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        CompileResult cr = engine.compile(code);

        ScriptVersionEntity v = new ScriptVersionEntity();
        v.setProtocolId(protocolId);
        v.setVersion(nextVersion);
        v.setLanguage("groovy");
        v.setCode(code);
        v.setCompileStatus(cr.isSuccess() ? "OK" : "ERROR");
        v.setCompileError(cr.isSuccess() ? null : cr.getError());
        v.setRemark(remark);
        v = versionRepository.save(v);

        if (cr.isSuccess()) {
            p.setCurrentVersionId(v.getId());
            protocolRepository.save(p);
            runtimeManager.reloadIfRunning(protocolId);
        }
        return toDto(v, true);
    }

    /** 切换/回滚到指定版本。 */
    @Transactional
    public ScriptVersionDto switchVersion(Long protocolId, Long versionId) {
        ProtocolEntity p = protocolRepository.findById(protocolId)
                .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + protocolId));
        ScriptVersionEntity v = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("脚本版本不存在: " + versionId));
        if (!v.getProtocolId().equals(protocolId)) {
            throw new IllegalArgumentException("该版本不属于此协议");
        }
        p.setCurrentVersionId(versionId);
        protocolRepository.save(p);
        runtimeManager.reloadIfRunning(protocolId);
        return toDto(v, true);
    }

    private ScriptVersionDto toDto(ScriptVersionEntity v, boolean withCode) {
        return new ScriptVersionDto(v.getId(), v.getProtocolId(), v.getVersion(), v.getLanguage(),
                v.getCompileStatus(), v.getCompileError(), v.getRemark(),
                withCode ? v.getCode() : null, v.getCreatedAt());
    }
}
