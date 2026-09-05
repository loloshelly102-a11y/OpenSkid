package openskid.command.commands;

import openskid.altmanager.AltManagerScreen;
import openskid.command.Command;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;

// Opens the OpenSkid alt manager. Registered by a sibling worker.
public class AltsCommand extends Command {
    public AltsCommand() {
        super(new ArrayList<String>(Arrays.asList("alts", "alt", "altmanager")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        Minecraft.getMinecraft().displayGuiScreen(new AltManagerScreen(null));
    }
}
