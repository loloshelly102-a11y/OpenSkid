package openskid.command.commands;

import net.minecraft.client.Minecraft;
import openskid.command.Command;
import openskid.setup.SetupScreen;
import openskid.setup.SetupState;
import openskid.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class SetupCommand extends Command {
    public SetupCommand() {
        super(new ArrayList<>(Arrays.asList("setupwizard", "setup")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        SetupState.reset();
        try {
            Minecraft.getMinecraft().displayGuiScreen(new SetupScreen());
        } catch (Exception e) {
            ChatUtil.sendFormatted("Could not open setup wizard.");
        }
    }
}
