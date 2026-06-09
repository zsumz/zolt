package com.example.large.service;

import com.example.large.core.CoreMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LargeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public String describe() {
        var cache = Caffeine.newBuilder().maximumSize(8).build(key -> CoreMessage.base());
        LOGGER.debug("large service cache initialized");
        try {
            return mapper.writeValueAsString(Map.of("message", cache.get("message")));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not render message", exception);
        }
    }
}
