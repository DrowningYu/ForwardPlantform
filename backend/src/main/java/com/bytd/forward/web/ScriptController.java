package com.bytd.forward.web;

import com.bytd.forward.service.ScriptService;
import com.bytd.forward.web.dto.ScriptSaveRequest;
import com.bytd.forward.web.dto.ScriptVersionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protocols/{protocolId}/scripts")
public class ScriptController {

    private final ScriptService service;

    public ScriptController(ScriptService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScriptVersionDto> listVersions(@PathVariable Long protocolId) {
        return service.listVersions(protocolId);
    }

    @GetMapping("/current")
    public ScriptVersionDto current(@PathVariable Long protocolId) {
        return service.getCurrent(protocolId);
    }

    @GetMapping("/{versionId}")
    public ScriptVersionDto version(@PathVariable Long protocolId, @PathVariable Long versionId) {
        return service.getVersion(versionId);
    }

    @PostMapping
    public ScriptVersionDto save(@PathVariable Long protocolId, @RequestBody ScriptSaveRequest req) {
        return service.saveNewVersion(protocolId, req.code, req.remark);
    }

    @PostMapping("/{versionId}/activate")
    public ScriptVersionDto activate(@PathVariable Long protocolId, @PathVariable Long versionId) {
        return service.switchVersion(protocolId, versionId);
    }
}
