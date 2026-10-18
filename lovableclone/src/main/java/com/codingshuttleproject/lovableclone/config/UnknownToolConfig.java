package com.codingshuttleproject.lovableclone.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Models sometimes call a tool that does not exist (e.g. gemini-3.8-flash called
 * "write_to_file"). Spring AI 1.0.0 then throws "No ToolCallback found" and the
 * whole chat stream dies.
 *
 * <p>Spring AI only asks this resolver for tools that are not registered on the
 * request, so this keeps its default lookup and, for an unknown name, returns a
 * tool that tells the model to use write_files instead of crashing.
 * Replaces the auto-configured resolver (it is {@code @ConditionalOnMissingBean}).
 */
@Slf4j
@Configuration
public class UnknownToolConfig {

    @Bean
    public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext,
                                                     List<ToolCallback> toolCallbacks,
                                                     List<ToolCallbackProvider> toolCallbackProviders) {
        // Same lookup Spring AI's ToolCallingAutoConfiguration builds by default.
        List<ToolCallback> allToolCallbacks = new ArrayList<>(toolCallbacks);
        toolCallbackProviders.forEach(provider -> allToolCallbacks.addAll(List.of(provider.getToolCallbacks())));
        ToolCallbackResolver defaultResolver = new DelegatingToolCallbackResolver(List.of(
                new StaticToolCallbackResolver(allToolCallbacks),
                SpringBeanToolCallbackResolver.builder().applicationContext(applicationContext).build()));

        return toolName -> {
            ToolCallback toolCallback = defaultResolver.resolve(toolName);
            return toolCallback != null ? toolCallback : unknownTool(toolName);
        };
    }

    private static ToolCallback unknownTool(String toolName) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(toolName)
                .description("Placeholder for a tool the model called that does not exist.")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                log.warn("Model called unknown tool '{}'; told it to use write_files instead", toolName);
                return "ERROR: there is no tool named '" + toolName + "'. Your only tools are read_files and "
                        + "write_files. Write all files in ONE write_files call (a list of path + content).";
            }
        };
    }
}
