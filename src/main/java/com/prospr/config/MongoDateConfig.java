package com.prospr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * Store LocalDate as UTC midnight so month filters match calendar dates
 * in India (IST). Without this, 01/09 becomes 31/08 18:30Z and drops out
 * of September dashboard totals.
 */
@Configuration
public class MongoDateConfig {

    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(
                List.of(
                        new LocalDateToDateConverter(),
                        new DateToLocalDateConverter()
                )
        );
    }

    private static final class LocalDateToDateConverter
            implements Converter<LocalDate, Date> {

        @Override
        public Date convert(LocalDate source) {
            return Date.from(
                    source.atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }
    }

    private static final class DateToLocalDateConverter
            implements Converter<Date, LocalDate> {

        @Override
        public LocalDate convert(Date source) {
            return source
                    .toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        }
    }
}
