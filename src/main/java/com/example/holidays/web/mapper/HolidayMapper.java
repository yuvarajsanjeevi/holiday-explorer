package com.example.holidays.web.mapper;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.web.dto.CountryHolidayCountResponse;
import com.example.holidays.web.dto.HolidayResponse;
import com.example.holidays.web.dto.LastHolidaysResponse;
import com.example.holidays.web.dto.SharedHolidayResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Domain types to the records the API returns, so the records stay plain data and wire-format choices sit in one place. */
@Component
public class HolidayMapper {

    public HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(holiday.date(), holiday.localName(), holiday.name());
    }

    /** {@code asOf} goes back to the caller because "the last three holidays" means nothing without a reference date. */
    public LastHolidaysResponse toLastHolidaysResponse(
            CountryCode countryCode, LocalDate asOf, Collection<Holiday> holidays) {
        return new LastHolidaysResponse(countryCode.value(), asOf, toHolidayResponses(holidays));
    }

    public List<HolidayResponse> toHolidayResponses(Collection<Holiday> holidays) {
        return holidays.stream().map(this::toResponse).toList();
    }

    public CountryHolidayCountResponse toResponse(CountryHolidayCount count) {
        return new CountryHolidayCountResponse(
                count.countryCode().value(), count.countryName(), count.count());
    }

    public List<CountryHolidayCountResponse> toCountResponses(Collection<CountryHolidayCount> counts) {
        return counts.stream().map(this::toResponse).toList();
    }

    public SharedHolidayResponse toResponse(SharedHoliday shared) {
        // Jackson writes a map in iteration order, so a HashMap here would shuffle the JSON keys
        // out of the order the countries were asked for.
        Map<String, List<String>> localNames = shared.localNames().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> List.copyOf(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new));
        return new SharedHolidayResponse(shared.date(), localNames);
    }

    public List<SharedHolidayResponse> toSharedResponses(Collection<SharedHoliday> shared) {
        return shared.stream().map(this::toResponse).toList();
    }
}
