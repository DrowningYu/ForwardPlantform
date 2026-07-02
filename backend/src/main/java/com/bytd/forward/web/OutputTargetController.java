package com.bytd.forward.web;

import com.bytd.forward.service.OutputTargetService;
import com.bytd.forward.web.dto.ConfigCarrierDto;
import com.bytd.forward.web.dto.OutputTargetRequest;
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
@RequestMapping("/api/output-targets")
public class OutputTargetController {

    private final OutputTargetService service;

    public OutputTargetController(OutputTargetService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfigCarrierDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ConfigCarrierDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ConfigCarrierDto create(@RequestBody OutputTargetRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ConfigCarrierDto update(@PathVariable Long id, @RequestBody OutputTargetRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
