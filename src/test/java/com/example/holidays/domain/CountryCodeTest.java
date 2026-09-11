package com.example.holidays.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.holidays.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CountryCodeTest {

    @Test
    @DisplayName("normalises to upper case so NL and nl are the same country")
    void normalisesCase() {
        assertThat(CountryCode.of("nl")).isEqualTo(CountryCode.of("NL"));
        assertThat(CountryCode.of("nL").value()).isEqualTo("NL");
    }

    @ParameterizedTest
    @DisplayName("rejects anything that is not two letters")
    @ValueSource(strings = {"", " ", "N", "NLD", "N1", "12", "N-"})
    void rejectsMalformed(String candidate) {
        assertThatThrownBy(() -> CountryCode.of(candidate)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("rejects a null code rather than letting it reach the regex")
    void rejectsNull() {
        assertThatThrownBy(() -> CountryCode.of(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("parses a list, trimming blanks and collapsing duplicates in order")
    void parsesList() {
        Set<CountryCode> codes = CountryCode.parseList(" NL , de,NL ,  , US");

        assertThat(codes).extracting(CountryCode::value).containsExactly("NL", "DE", "US");
    }

    @Test
    @DisplayName("rejects an empty list")
    void rejectsEmptyList() {
        assertThatThrownBy(() -> CountryCode.parseList("  ,  ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CountryCode.parseList("")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("rejects a missing list the same way as an empty one")
    void rejectsNullList() {
        assertThatThrownBy(() -> CountryCode.parseList(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("At least one country code is required.");
    }
}
