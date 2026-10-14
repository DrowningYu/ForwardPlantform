package com.bytd.forward.web;

import com.bytd.forward.domain.entity.ProtocolGroupEntity;
import com.bytd.forward.service.ProtocolGroupService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/protocol-groups")
public class ProtocolGroupController {
    private final ProtocolGroupService service;
    public ProtocolGroupController(ProtocolGroupService service) { this.service = service; }
    public record GroupRequest(String name, Integer sortOrder) {}
    @GetMapping
    public List<ProtocolGroupEntity> list() { return service.list(); }
    @PostMapping
    public ProtocolGroupEntity create(@RequestBody GroupRequest req) {
        return service.save(null, req.name(), req.sortOrder());
    }
    @PutMapping("/{id}")
    public ProtocolGroupEntity update(@PathVariable Long id, @RequestBody GroupRequest req) {
        return service.save(id, req.name(), req.sortOrder());
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
