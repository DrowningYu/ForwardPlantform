package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.runtime.RuntimeStatus;
import com.bytd.forward.web.dto.ScriptVersionDto;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
public class ScriptService {
    private final ScriptVersionRepository versions;
    private final ProtocolRepository protocols;
    private final ScriptEngineService engine;
    private final ProtocolRuntimeManager runtime;
    private final ProtocolMutationExecutor mutations;

    public ScriptService(ScriptVersionRepository versions, ProtocolRepository protocols,
                         ScriptEngineService engine, ProtocolRuntimeManager runtime,
                         ProtocolMutationExecutor mutations) {
        this.versions = versions;
        this.protocols = protocols;
        this.engine = engine;
        this.runtime = runtime;
        this.mutations = mutations;
    }

    public List<ScriptVersionDto> listVersions(Long protocolId) {
        ProtocolEntity p = protocol(protocolId);
        RuntimeStatus status = runtime.status(protocolId).orElse(null);
        return versions.findByProtocolIdOrderByVersionDesc(protocolId).stream()
                .map(v -> dto(v, false, p, status)).toList();
    }

    public ScriptVersionDto getVersion(Long protocolId, Long versionId) {
        return dto(version(protocolId, versionId), true, protocol(protocolId), runtime.status(protocolId).orElse(null));
    }

    public ScriptVersionDto getCurrent(Long protocolId) {
        ProtocolEntity p = protocol(protocolId);
        return p.getCurrentVersionId() == null ? null : getVersion(protocolId, p.getCurrentVersionId());
    }

    public ScriptVersionDto saveNewVersion(Long protocolId, String code, String remark) {
        validate(code, remark);
        ScriptVersionEntity saved = mutations.execute(protocolId, () -> {
            ProtocolEntity p = lockedProtocol(protocolId);
            CompileResult cr = engine.compile(code);
            if (cr.getCompiled() != null) cr.getCompiled().close(); // 校验产物不参与运行，及时释放 ClassLoader。
            ScriptVersionEntity v = new ScriptVersionEntity();
            v.setProtocolId(protocolId);
            // 兼容升级后临时回退旧程序、旧程序仍新增版本的情况。
            int next = Math.max(p.getNextScriptVersion(), versions.findTopByProtocolIdOrderByVersionDesc(protocolId)
                    .map(previous -> Math.addExact(previous.getVersion(), 1)).orElse(1));
            v.setVersion(next);
            p.setNextScriptVersion(Math.addExact(next, 1));
            v.setLanguage("groovy");
            v.setCode(code);
            v.setRemark(remark);
            v.setCompileStatus(cr.isSuccess() ? "OK" : "ERROR");
            v.setCompileError(cr.isSuccess() ? null : cr.getError());
            v = versions.saveAndFlush(v);
            if (cr.isSuccess()) p.setCurrentVersionId(v.getId());
            protocols.save(p);
            return v;
        }, v -> {
            if ("OK".equals(v.getCompileStatus())) reload(protocolId);
        });
        return getVersion(protocolId, saved.getId());
    }

    public ScriptVersionDto revise(Long protocolId, Long versionId, String code, String remark, Long revision) {
        validate(code, remark);
        mutations.execute(protocolId, () -> {
            ProtocolEntity p = lockedProtocol(protocolId);
            ScriptVersionEntity v = version(protocolId, versionId);
            if (revision == null || revision.longValue() != v.getRevision()) {
                throw new IllegalStateException("版本已被其他操作修改，请重新载入后修订");
            }
            boolean codeChanged = !Objects.equals(v.getCode(), code);
            long previousRevision = v.getRevision();
            if (codeChanged) requireCompiles(code);
            v.setCode(code);
            v.setRemark(remark);
            if (codeChanged) {
                v.setCompileStatus("OK");
                v.setCompileError(null);
            }
            versions.saveAndFlush(v);
            return new RevisionResult(v, codeChanged && Objects.equals(p.getCurrentVersionId(), versionId), codeChanged, previousRevision);
        }, result -> {
            if (result.reload()) reload(protocolId);
            else if (!result.codeChanged()) runtime.updateScriptRevision(protocolId, versionId,
                    result.previousRevision(), result.version().getRevision());
        });
        return getVersion(protocolId, versionId);
    }

    public ScriptVersionDto switchVersion(Long protocolId, Long versionId) {
        mutations.execute(protocolId, () -> {
            ProtocolEntity p = lockedProtocol(protocolId);
            ScriptVersionEntity v = version(protocolId, versionId);
            if (Objects.equals(p.getCurrentVersionId(), versionId)) return false;
            requireCompiles(v.getCode());
            v.setCompileStatus("OK");
            v.setCompileError(null);
            versions.saveAndFlush(v);
            p.setCurrentVersionId(versionId);
            protocols.save(p);
            return true;
        }, changed -> { if (changed) reload(protocolId); });
        return getVersion(protocolId, versionId);
    }

    public void delete(Long protocolId, Long versionId) {
        mutations.execute(protocolId, () -> {
            ProtocolEntity p = lockedProtocol(protocolId);
            ScriptVersionEntity v = version(protocolId, versionId);
            if (Objects.equals(p.getCurrentVersionId(), versionId)
                    || runtime.status(protocolId).filter(s -> "RUNNING".equals(s.status())
                    && Objects.equals(s.scriptVersionId(), versionId)).isPresent()) {
                throw new IllegalStateException("不能删除当前使用的版本，请先切换到其他有效版本");
            }
            versions.delete(v);
            versions.flush();
            return true;
        }, ignored -> {});
    }

    private void reload(Long protocolId) {
        try { runtime.reloadIfRunning(protocolId); }
        catch (Exception e) {
            throw new IllegalStateException("脚本已保存，但协议重载失败，请检查运行状态后重试启动", e);
        }
    }

    private void requireCompiles(String code) {
        CompileResult result = engine.compile(code);
        if (result.getCompiled() != null) result.getCompiled().close();
        if (!result.isSuccess()) throw new IllegalArgumentException("编译失败，原版本未修改: " + result.getError());
    }

    private void validate(String code, String remark) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("脚本代码不能为空");
        if (remark != null && remark.length() > 512) throw new IllegalArgumentException("版本备注不能超过 512 字");
    }

    private ProtocolEntity protocol(Long id) {
        return protocols.findById(id).orElseThrow(() -> new IllegalArgumentException("协议不存在: " + id));
    }

    private ProtocolEntity lockedProtocol(Long id) {
        return protocols.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("协议不存在: " + id));
    }

    private ScriptVersionEntity version(Long protocolId, Long versionId) {
        ScriptVersionEntity v = versions.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("脚本版本不存在: " + versionId));
        if (!Objects.equals(v.getProtocolId(), protocolId)) throw new IllegalArgumentException("该版本不属于此协议");
        return v;
    }

    private ScriptVersionDto dto(ScriptVersionEntity v, boolean withCode, ProtocolEntity p, RuntimeStatus status) {
        boolean active = status != null && "RUNNING".equals(status.status())
                && Objects.equals(status.scriptVersionId(), v.getId()) && status.scriptRevision() == v.getRevision();
        return new ScriptVersionDto(v.getId(), v.getProtocolId(), v.getVersion(), v.getLanguage(),
                v.getCompileStatus(), v.getCompileError(), v.getRemark(), withCode ? v.getCode() : null,
                v.getCreatedAt(), v.getUpdatedAt(), v.getRevision(),
                Objects.equals(p.getCurrentVersionId(), v.getId()), active);
    }

    private record RevisionResult(ScriptVersionEntity version, boolean reload, boolean codeChanged, long previousRevision) {}
}
