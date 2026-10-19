package com.dispatch.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Jackson module that serialises whole-number doubles as {@code 10} rather than {@code 10.0},
 * matching the response format in the specification. Spring Boot registers every
 * {@link com.fasterxml.jackson.databind.Module} bean with the application's ObjectMapper.
 */
@Component
public class WholeNumberDoubleModule extends SimpleModule {

    public WholeNumberDoubleModule() {
        super("WholeNumberDoubleModule");
        WholeNumberDoubleSerializer serializer = new WholeNumberDoubleSerializer();
        addSerializer(Double.class, serializer);
        addSerializer(Double.TYPE, serializer);
    }

    /**
     * Writes integral doubles (within the exactly representable range) as JSON integers.
     */
    static final class WholeNumberDoubleSerializer extends StdSerializer<Double> {

        /** Every integer below 2^53 in magnitude is exactly representable as a double. */
        private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_992d;

        WholeNumberDoubleSerializer() {
            super(Double.class);
        }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            double d = value;
            if (d == Math.rint(d) && Math.abs(d) < MAX_EXACT_INTEGER) {
                gen.writeNumber((long) d);
            } else {
                gen.writeNumber(d);
            }
        }
    }
}
