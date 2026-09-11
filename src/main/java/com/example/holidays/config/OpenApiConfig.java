package com.example.holidays.config;

import com.example.holidays.exception.ErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA = "ApiError";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA;

    @Bean
    OpenAPI holidayExplorerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Holiday Explorer API")
                .version("1.0.0")
                .description("""
                        Queries over the public holiday data published by date.nager.at.

                        The data and the upstream API originate from https://date.nager.at and are used \
                        here for a coding assignment; no ownership is claimed over either.""")
                .license(new License().name("Upstream data: date.nager.at").url("https://date.nager.at")))
                // Relative, so the checked-in spec is not pinned to whatever host generated it.
                .servers(List.of(new Server().url("/")));
    }

    /** The responses themselves are declared per endpoint, since what a 400 means differs between them. */
    @Bean
    OpenApiCustomizer errorSchemaCustomizer() {
        return openApi -> openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .description("The body of every failure response")
                .addProperty("type", new StringSchema().format("uri")
                        .example(ErrorCode.UNKNOWN_COUNTRY.type().toString()))
                .addProperty("title", new StringSchema().example(ErrorCode.UNKNOWN_COUNTRY.title()))
                .addProperty("status", new IntegerSchema().example(ErrorCode.UNKNOWN_COUNTRY.status().value()))
                .addProperty("detail", new StringSchema().example("Unknown country code(s): XX."))
                .addProperty("instance", new StringSchema().format("uri")
                        .example("/api/v1/countries/XX/holidays/last"));
    }


    /** Copied from a real response, so the docs match what a caller actually gets. */
    private static Map<String, Object> example(ErrorCode errorCode, String detail, String instance) {
        Map<String, Object> example = LinkedHashMap.newLinkedHashMap(5);
        example.put("type", errorCode.type().toString());
        example.put("title", errorCode.title());
        example.put("status", errorCode.status().value());
        example.put("detail", detail);
        example.put("instance", instance);
        return example;
    }
}
