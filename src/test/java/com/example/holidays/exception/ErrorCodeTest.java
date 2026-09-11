package com.example.holidays.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.holidays.constant.ApplicationConstants;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/**
 * Guards the published error contract. These codes and titles go out in responses and in the OpenAPI
 * document, so a caller may match on them - changing one should be deliberate, not a slip.
 */
class ErrorCodeTest {

    @Test
    @DisplayName("each failure keeps the status, code and title the API documents")
    void exposesTheDocumentedContract() {
        assertThat(ErrorCode.INVALID_REQUEST.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_REQUEST.code()).isEqualTo("invalid-request");
        assertThat(ErrorCode.INVALID_REQUEST.title()).isEqualTo("Invalid request");

        assertThat(ErrorCode.UNKNOWN_COUNTRY.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.UNKNOWN_COUNTRY.code()).isEqualTo("unknown-country");
        assertThat(ErrorCode.UNKNOWN_COUNTRY.title()).isEqualTo("Unknown country");

        assertThat(ErrorCode.UPSTREAM_UNAVAILABLE.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.UPSTREAM_UNAVAILABLE.code()).isEqualTo("upstream-unavailable");
        assertThat(ErrorCode.UPSTREAM_UNAVAILABLE.title()).isEqualTo("Holiday API unavailable");

        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.INTERNAL_ERROR.code()).isEqualTo("internal-error");
        assertThat(ErrorCode.INTERNAL_ERROR.title()).isEqualTo("Internal error");
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("the type URI is the code resolved against the base")
    void typeResolvesTheCodeAgainstTheBase(ErrorCode errorCode) {
        assertThat(errorCode.type())
                .hasToString(ApplicationConstants.ERROR_TYPE_BASE + errorCode.code())
                .hasScheme("https");
    }

    @Test
    @DisplayName("no two failures share a code, so the type URI identifies exactly one of them")
    void codesAreUnique() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code))
                .doesNotHaveDuplicates()
                .hasSize(ErrorCode.values().length);
    }
}
