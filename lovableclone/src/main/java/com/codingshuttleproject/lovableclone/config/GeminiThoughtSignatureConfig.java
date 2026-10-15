package com.codingshuttleproject.lovableclone.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Gemini 3 rejects (HTTP 400) any assistant tool call sent back without the
 * {@code thought_signature} it returned. Spring AI 1.0.0's OpenAI client drops
 * that field, so the request after {@code read_files} always failed.
 *
 * <p>This rewrites outgoing Gemini chat requests and stamps the validator-skip
 * value Google documents for exactly this case onto every tool call missing a
 * signature: https://ai.google.dev/gemini-api/docs/thought-signatures
 * Google notes reasoning across tool calls may be slightly weaker than with the
 * real signature; dropping this needs a Spring AI version that echoes it.
 *
 * <p>Spring AI builds its clients from Boot's RestClient/WebClient builders, so
 * the customizers cover both the blocking and the streaming paths.
 */
@Slf4j
@Configuration
public class GeminiThoughtSignatureConfig {

    private static final String GEMINI_HOST = "generativelanguage.googleapis.com";
    private static final String COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String SKIP_SIGNATURE = "skip_thought_signature_validator";

    private final ObjectMapper objectMapper;

    public GeminiThoughtSignatureConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public RestClientCustomizer geminiThoughtSignatureRestClientCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (!isGeminiChat(request.getURI())) {
                return execution.execute(request, body);
            }
            byte[] patched = addMissingSignatures(body);
            request.getHeaders().setContentLength(patched.length);
            return execution.execute(request, patched);
        });
    }

    @Bean
    public WebClientCustomizer geminiThoughtSignatureWebClientCustomizer() {
        return builder -> builder.filter(signatureFilter());
    }

    private ExchangeFilterFunction signatureFilter() {
        return (request, next) -> {
            if (!isGeminiChat(request.url())) {
                return next.exchange(request);
            }
            ClientRequest patched = ClientRequest.from(request)
                    .body((outputMessage, context) -> request.body().insert(
                            new ClientHttpRequestDecorator(outputMessage) {
                                @Override
                                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                                    return DataBufferUtils.join(body).flatMap(buffer -> {
                                        byte[] original = new byte[buffer.readableByteCount()];
                                        buffer.read(original);
                                        DataBufferUtils.release(buffer);
                                        byte[] rewritten = addMissingSignatures(original);
                                        getHeaders().setContentLength(rewritten.length);
                                        return super.writeWith(Mono.just(bufferFactory().wrap(rewritten)));
                                    });
                                }
                            }, context))
                    .build();
            return next.exchange(patched);
        };
    }

    private static boolean isGeminiChat(URI uri) {
        return GEMINI_HOST.equals(uri.getHost()) && uri.getPath().endsWith(COMPLETIONS_SUFFIX);
    }

    private byte[] addMissingSignatures(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode messages = root.path("messages");
            boolean changed = false;
            for (JsonNode message : messages) {
                for (JsonNode toolCall : message.path("tool_calls")) {
                    if (toolCall instanceof ObjectNode call
                            && call.path("extra_content").path("google").path("thought_signature").isMissingNode()) {
                        call.withObject("/extra_content/google").put("thought_signature", SKIP_SIGNATURE);
                        changed = true;
                    }
                }
            }
            return changed ? objectMapper.writeValueAsBytes(root) : body;
        }
        catch (Exception e) {
            log.warn("Could not add Gemini thought signatures, sending request unchanged", e);
            return body;
        }
    }
}
