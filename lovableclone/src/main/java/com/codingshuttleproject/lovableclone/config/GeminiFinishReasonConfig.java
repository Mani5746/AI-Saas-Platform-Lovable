package com.codingshuttleproject.lovableclone.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini's OpenAI-compatible endpoint can return finish reasons outside the
 * OpenAI set, e.g. {@code "function_call_filter: MALFORMED_FUNCTION_CALL"}.
 * Spring AI 1.0.0 parses chunks with its own static ObjectMapper and fails the
 * whole stream on an unknown enum value. Reading unknown values as null lets
 * the stream end normally instead.
 *
 * <p>Runs at startup, before any chunk is parsed, so the setting applies to
 * every deserializer the mapper later builds.
 */
@Configuration
public class GeminiFinishReasonConfig {

    @PostConstruct
    void tolerateUnknownFinishReasons() {
        ModelOptionsUtils.OBJECT_MAPPER.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }
}
