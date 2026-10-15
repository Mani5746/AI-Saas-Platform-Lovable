package com.codingshuttleproject.lovableclone.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Checks on startup that the configured OpenRouter model actually exists, is
 * currently being served, and advertises tool calling.
 *
 * <p>Without this the first failure surfaces deep inside a streaming request as
 * an opaque 400/404 from OpenRouter — e.g. {@code nex-agi/nex-n2.5-mini:free}
 * resolves to a real model page but has zero serving endpoints, so every
 * completion fails. The agent in {@code AiGenerationServiceImpl} drives
 * {@code CodeGenerationTools}, so a model without tool support silently
 * degrades into plain chat instead of reading project files.
 *
 * <p>Purely advisory: it logs and never blocks startup, so an OpenRouter
 * outage or an air-gapped run cannot stop the app from booting.
 */
@Slf4j
@Component
public class OpenRouterModelValidator {

    private static final String OPENROUTER_HOST = "openrouter.ai";

    private final String baseUrl;
    private final String model;
    private final RestClient restClient = RestClient.create();

    public OpenRouterModelValidator(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                    @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!baseUrl.contains(OPENROUTER_HOST)) {
            log.info("AI base-url is {}, skipping OpenRouter model validation", baseUrl);
            return;
        }

        try {
            Map<String, Object> body = restClient.get()
                    .uri(baseUrl + "/v1/models/{model}/endpoints", model)
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = body == null ? null : (Map<String, Object>) body.get("data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> endpoints = data == null
                    ? List.of()
                    : (List<Map<String, Object>>) data.getOrDefault("endpoints", List.of());

            if (endpoints.isEmpty()) {
                log.error("""
                        OpenRouter model '{}' has no serving endpoints — every completion will fail.
                        Pick an id listed at https://openrouter.ai/models and set OPENROUTER_MODEL.
                        A ':free' suffix only works when that exact free variant is published.""", model);
                return;
            }

            boolean supportsTools = endpoints.stream().anyMatch(OpenRouterModelValidator::supportsTools);
            if (!supportsTools) {
                log.warn("""
                        OpenRouter model '{}' does not advertise tool calling.
                        The code-generation agent cannot call read_files and will answer blind.
                        Set OPENROUTER_MODEL to a tool-capable id.""", model);
                return;
            }

            log.info("OpenRouter model '{}' is live on {} endpoint(s) with tool calling enabled",
                    model, endpoints.size());
        }
        catch (Exception ex) {
            log.warn("Could not verify OpenRouter model '{}': {}", model, ex.getMessage());
        }
    }

    private static boolean supportsTools(Map<String, Object> endpoint) {
        Object params = endpoint.get("supported_parameters");
        return params instanceof List<?> list && list.contains("tools");
    }
}
