package lk.swiftdrop.reporting.infrastructure.messaging;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.reporting.support.ContractEvents;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventReaderTest {

    private final EventReader reader = new EventReader(JsonMapper.builder().build());

    @Test
    void readsTheContractExample() {
        EventEnvelope<ParcelCreated> event = reader.read(ContractEvents.raw("parcel-created.v1"),
                ParcelCreated.class, ParcelCreated.TYPE, ParcelCreated.VERSION);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("6f1c7a52-3d0b-4c43-9a8e-0f6a2c1d9b10"));
        assertThat(event.data().dropoff().zoneCode()).isEqualTo("CP-KDY");
    }

    @Test
    void ignoresFieldsAddedByNewerProducers() {
        String json = ContractEvents.raw("parcel-created.v1").replaceFirst("\\{", "{\"addedLater\": true,");

        assertThat(reader.read(json, ParcelCreated.class, ParcelCreated.TYPE, ParcelCreated.VERSION).data()).isNotNull();
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> reader.read("not json", ParcelCreated.class, ParcelCreated.TYPE, 1))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsAnEventOfAnotherType() {
        assertThatThrownBy(() -> reader.read(ContractEvents.raw("parcel-created.v1"), ParcelStatusChanged.class,
                ParcelStatusChanged.TYPE, ParcelStatusChanged.VERSION))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("Expected ParcelStatusChanged");
    }

    @Test
    void rejectsAnUnknownVersion() {
        String v2 = ContractEvents.raw("parcel-created.v1").replace("\"eventVersion\": 1", "\"eventVersion\": 2");

        assertThatThrownBy(() -> reader.read(v2, ParcelCreated.class, ParcelCreated.TYPE, ParcelCreated.VERSION))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("version 2");
    }

    @Test
    void rejectsAnEnvelopeWithoutData() {
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"ParcelCreated\",\"eventVersion\":1}";

        assertThatThrownBy(() -> reader.read(json, ParcelCreated.class, ParcelCreated.TYPE, ParcelCreated.VERSION))
                .isInstanceOf(MalformedEventException.class);
    }
}
