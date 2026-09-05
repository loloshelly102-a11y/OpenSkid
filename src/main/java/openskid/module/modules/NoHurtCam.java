package openskid.module.modules;

import openskid.module.Module;
import openskid.property.properties.PercentProperty;

public class NoHurtCam extends Module {
    public final PercentProperty multiplier = new PercentProperty("multiplier", 0);

    public NoHurtCam() {
        super("NoHurtCam", false, true, "Removes or reduces the hurt camera shake.");
    }
}
