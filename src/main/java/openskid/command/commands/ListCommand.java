package openskid.command.commands;

import openskid.OpenSkid;
import openskid.command.Command;
import openskid.module.Module;
import openskid.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "openskid")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!OpenSkid.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", OpenSkid.clientName));
            for (Module module : OpenSkid.moduleManager.allModules()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
