package com.bytd.forward.web;

import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.runtime.RuntimeStatus;
import com.bytd.forward.service.ProtocolBindingWarningService;
import com.bytd.forward.service.ProtocolService;
import com.bytd.forward.service.StartCheckMode;
import com.bytd.forward.web.dto.BindingCheckResultDto;
import com.bytd.forward.web.dto.ProtocolDto;
import com.bytd.forward.web.dto.ProtocolRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protocols")
public class ProtocolController {

    private final ProtocolService service;
    private final ProtocolRuntimeManager runtimeManager;
    private final ProtocolBindingWarningService bindingWarningService;

    public ProtocolController(ProtocolService service,
                              ProtocolRuntimeManager runtimeManager,
                              ProtocolBindingWarningService bindingWarningService) {
        this.service = service;
        this.runtimeManager = runtimeManager;
        this.bindingWarningService = bindingWarningService;
    }

    @GetMapping
    public List<ProtocolDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProtocolDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ProtocolDto create(@RequestBody ProtocolRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ProtocolDto update(@PathVariable Long id, @RequestBody ProtocolRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/binding-warnings")
    public BindingCheckResultDto bindingWarnings(
            @RequestParam(required = false) Long protocolId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Long outputTargetId) {
        return bindingWarningService.analyze(protocolId, sourceId, outputTargetId, StartCheckMode.CONFIG);
    }

    @GetMapping("/{id}/start-check")
    public BindingCheckResultDto startCheck(@PathVariable Long id) {
        return bindingWarningService.analyzeForStart(id);
    }

    @PostMapping("/{id}/start")
    public ProtocolDto start(@PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean acknowledgeWarnings) {
        service.start(id, acknowledgeWarnings);
        return service.get(id);
    }

    @PostMapping("/{id}/stop")
    public ProtocolDto stop(@PathVariable Long id) {
        service.stop(id);
        return service.get(id);
    }

    @PostMapping("/{id}/restart")
    public ProtocolDto restart(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean acknowledgeWarnings) {
        service.restart(id, acknowledgeWarnings);
        return service.get(id);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<RuntimeStatus> status(@PathVariable Long id) {
        return runtimeManager.status(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
