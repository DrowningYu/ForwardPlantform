package com.bytd.forward.config;

import com.bytd.forward.engine.api.JsonHelper;
import com.bytd.forward.engine.api.TimeHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public JsonHelper jsonHelper(ObjectMapper objectMapper) {
        return new JsonHelper(objectMapper);
    }

    @Bean
    public TimeHelper timeHelper() {
        return new TimeHelper();
    }
}
