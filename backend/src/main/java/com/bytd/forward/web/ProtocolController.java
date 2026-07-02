package com.bytd.forward.web;

import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.runtime.RuntimeStatus;
import com.bytd.forward.service.ProtocolService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protocols")
public class ProtocolController {

    private final ProtocolService service;
    private final ProtocolRuntimeManager runtimeManager;

    public ProtocolController(ProtocolService service, ProtocolRuntimeManager runtimeManager) {
        this.service = service;
        this.runtimeManager = runtimeManager;
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

    @PostMapping("/{id}/start")
    public ProtocolDto start(@PathVariable Long id) {
        service.start(id);
        return service.get(id);
    }

    @PostMapping("/{id}/stop")
    public ProtocolDto stop(@PathVariable Long id) {
        service.stop(id);
        return service.get(id);
    }

    @PostMapping("/{id}/restart")
    public ProtocolDto restart(@PathVariable Long id) {
        service.restart(id);
        return service.get(id);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<RuntimeStatus> status(@PathVariable Long id) {
        return runtimeManager.status(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
