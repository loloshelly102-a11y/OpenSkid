package openskid.events;

import openskid.event.events.callables.EventCancellable;
import openskid.event.types.EventType;

public class TickEvent extends EventCancellable {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return this.type;
    }
}