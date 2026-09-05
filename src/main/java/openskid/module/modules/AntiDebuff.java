package openskid.module.modules;

import openskid.module.Module;
import openskid.property.properties.BooleanProperty;

public class AntiDebuff extends Module {
    public final BooleanProperty blindness = new BooleanProperty("blindness", true);
    public final BooleanProperty nausea = new BooleanProperty("nausea", true);

    public AntiDebuff() {
        super("AntiDebuff", false, false, "Removes blindness and nausea potion effects.");
    }
}
