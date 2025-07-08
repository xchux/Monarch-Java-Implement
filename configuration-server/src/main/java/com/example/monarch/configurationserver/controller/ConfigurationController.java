package com.example.monarch.configurationserver.controller;

import com.example.monarch.configurationserver.model.SystemConfig;
import com.example.monarch.configurationserver.repository.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config/v1")
public class ConfigurationController {

    private final SystemConfigRepository repository;

    public ConfigurationController(SystemConfigRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public SystemConfig saveConfig(@RequestBody SystemConfig config) {
        return repository.save(config);
    }

    @GetMapping("/{configKey}")
    public ResponseEntity<SystemConfig> getConfig(@PathVariable String configKey) {
        return repository.findByConfigKey(configKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
