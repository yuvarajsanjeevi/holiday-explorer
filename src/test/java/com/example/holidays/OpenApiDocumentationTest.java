package com.example.holidays;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.holidays.client.NagerDateClient;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards the published contract. A spec that only describes the happy path is worse than none - the
 * caller writes no error handling. These fail the build if an endpoint stops documenting failures.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(RedisTestContainer.class)
@EnabledIf("com.example.holidays.RedisTestContainer#dockerAvailable")
class OpenApiDocumentationTest {

    private static final List<String> OPERATIONS = List.of(
            "/api/v1/countries/{countryCode}/holidays/last",
            "/api/v1/holidays/weekday-counts",
            "/api/v1/holidays/shared");

    private static final List<String> FAILURE_STATUSES = List.of("400", "404", "502", "500");

    private static final String ERROR_JSON = "application/json";

    @MockitoBean
    private NagerDateClient nagerDateClient;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode spec;

    @BeforeEach
    void fetchSpec() throws UnsupportedEncodingException {
        String body = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
        spec = objectMapper.readTree(body);
    }

    private JsonNode responses(String path) {
        JsonNode responses = spec.path("paths").path(path).path("get").path("responses");
        assertThat(responses.isObject()).as("operation GET %s must be documented", path).isTrue();
        return responses;
    }

    @Test
    @DisplayName("every operation documents success and all four failure modes")
    void everyOperationDocumentsItsFailures() {
        for (String path : OPERATIONS) {
            JsonNode responses = responses(path);
            assertThat(responses.propertyNames())
                    .as("documented statuses for GET %s", path)
                    .contains("200", "400", "404", "502", "500");
            responses.propertyStream().forEach(entry ->
                    assertThat(entry.getValue().path("description").asString())
                            .as("GET %s response %s needs a description", path, entry.getKey())
                            .isNotBlank());
        }
    }

    @Test
    @DisplayName("every failure points at the shared error schema")
    void failuresPointAtTheSharedErrorSchema() {
        for (String path : OPERATIONS) {
            for (String status : FAILURE_STATUSES) {
                JsonNode content = responses(path).path(status).path("content").path(ERROR_JSON);
                assertThat(content.path("schema").path("$ref").asString())
                        .as("GET %s response %s schema", path, status)
                        .isEqualTo("#/components/schemas/ApiError");
            }
        }
    }

    @Test
    @DisplayName("each failure carries a worked example a caller can match on")
    void eachFailureCarriesAnExample() {
        for (String path : OPERATIONS) {
            for (String status : FAILURE_STATUSES) {
                JsonNode example = responses(path).path(status).path("content")
                        .path(ERROR_JSON).path("example");
                assertThat(example.isObject()).as("GET %s response %s example", path, status).isTrue();
                assertThat(example.path("status").asInt()).isEqualTo(Integer.parseInt(status));
                assertThat(example.path("type").asString()).startsWith("https://");
                assertThat(example.path("title").asString()).isNotBlank();
                assertThat(example.path("detail").asString()).isNotBlank();

                // Each example must point at its own endpoint. They all used to point at the same
                // one, so last-holidays showed a year error for a path that takes no year.
                String documentedPath = path.replace("{countryCode}", "NL");
                assertThat(example.path("instance").asString())
                        .as("GET %s response %s example should reference its own path", path, status)
                        .isEqualTo(documentedPath);
            }
        }
    }

    @Test
    @DisplayName("success responses are declared as JSON rather than */*")
    void successIsDeclaredAsJson() {
        for (String path : OPERATIONS) {
            assertThat(responses(path).path("200").path("content").propertyNames())
                    .as("GET %s success content types", path)
                    .containsExactly("application/json");
        }
    }

    @Test
    @DisplayName("the checked-in openapi.yaml still matches the code")
    void checkedInSpecIsUpToDate() throws Exception {
        String live = mvc.get().uri("/v3/api-docs.yaml").exchange().getResponse().getContentAsString();
        String checkedIn = Files.readString(Path.of("src/main/resources/static/openapi.yaml"));

        assertThat(checkedIn.strip())
                .as("openapi.yaml is generated. Regenerate it with:%n"
                        + "  ./mvnw spring-boot:run%n"
                        + "  curl -s localhost:8080/v3/api-docs.yaml -o src/main/resources/static/openapi.yaml")
                .isEqualTo(live.strip());
    }

    @Test
    @DisplayName("the error schema is published for clients to generate from")
    void errorSchemaIsPublished() {
        JsonNode errorSchema = spec.path("components").path("schemas").path("ApiError");

        assertThat(errorSchema.path("properties").propertyNames())
                .contains("type", "title", "status", "detail", "instance");
    }
}
