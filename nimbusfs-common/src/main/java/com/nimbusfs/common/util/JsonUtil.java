package com.nimbusfs.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Singleton Jackson ObjectMapper configured for NimbusFS use.
 *
 * Settings:
 *  - Java 8 date/time types (Instant, LocalDateTime) supported via JavaTimeModule
 *  - Unknown JSON fields ignored (forward-compatible deserialization)
 *  - Dates serialized as timestamps (epoch millis) for wire efficiency
 */
public final class JsonUtil {

    private static final ObjectMapper INSTANCE = createMapper();

    private JsonUtil() {}

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /** Returns the shared ObjectMapper instance. */
    public static ObjectMapper get() {
        return INSTANCE;
    }
}
