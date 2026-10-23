package ua.newerp.api;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/** JSON details that must match the .NET API. Spring Boot adds every JacksonModule bean to its JSON mapper. */
@Configuration
class JsonConfig {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** A time of day as "21:30:00" like .NET TimeOnly (Jackson would write "21:30"). */
    @Bean
    JacksonModule timeOfDayModule() {
        return new SimpleModule("time-of-day").addSerializer(LocalTime.class, new ValueSerializer<>() {
            @Override
            public void serialize(LocalTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.format(TIME));
            }
        });
    }
}
