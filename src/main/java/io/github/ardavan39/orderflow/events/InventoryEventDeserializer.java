package io.github.ardavan39.orderflow.events;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class InventoryEventDeserializer extends ObjectMapperDeserializer<InventoryEvent> {

    public InventoryEventDeserializer() {
        super(InventoryEvent.class);
    }
}
