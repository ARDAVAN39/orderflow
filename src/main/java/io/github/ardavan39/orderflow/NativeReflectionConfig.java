package io.github.ardavan39.orderflow;

import io.quarkus.kafka.client.serialization.ObjectMapperSerializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Kafka creates serializers by reflection from their class names. The ones named in
 * application.properties are registered automatically for the native image; this also covers the
 * ones the dead-letter queue producers use.
 */
@RegisterForReflection(targets = ObjectMapperSerializer.class)
public final class NativeReflectionConfig {

    private NativeReflectionConfig() {
    }
}
