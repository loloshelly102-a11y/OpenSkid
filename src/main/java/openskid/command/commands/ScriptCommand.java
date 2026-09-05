package openskid.command.commands;

import openskid.OpenSkid;
import openskid.command.Command;
import openskid.script.ScriptManager;
import openskid.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Registered by a sibling worker in OpenSkid.java. Kept thin: parse, delegate,
// report. All real work lives in ScriptManager.
public class ScriptCommand extends Command {
    public ScriptCommand() {
        super(new ArrayList<String>(Arrays.asList("script", "scripts")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            sendUsage(args);
            return;
        }
        ScriptManager manager = ScriptManager.getInstance();
        String sub = args.get(1).toLowerCase(Locale.ROOT);
        if (sub.equals("l")) {
            sub = "list";
        } else if (sub.equals("r")) {
            sub = "reload";
        }
        switch (sub) {
            case "list":
                listScripts(manager);
                return;
            case "load":
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(String.format("%sUsage: .%s load <&oname&r>&r", OpenSkid.clientName, args.get(0)));
                    return;
                }
                manager.load(args.get(2));
                return;
            case "unload":
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(String.format("%sUsage: .%s unload <&oname&r>&r", OpenSkid.clientName, args.get(0)));
                    return;
                }
                manager.unload(args.get(2));
                return;
            case "reload":
                if (args.size() < 3) {
                    manager.reloadAll();
                    ChatUtil.sendFormatted(String.format("%sScripts reloaded&r", OpenSkid.clientName));
                    return;
                }
                manager.reload(args.get(2));
                return;
            case "enable":
            case "on":
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(String.format("%sUsage: .%s enable <&oname&r>&r", OpenSkid.clientName, args.get(0)));
                    return;
                }
                manager.setEnabled(args.get(2), true);
                return;
            case "disable":
            case "off":
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(String.format("%sUsage: .%s disable <&oname&r>&r", OpenSkid.clientName, args.get(0)));
                    return;
                }
                manager.setEnabled(args.get(2), false);
                return;
            default:
                ChatUtil.sendFormatted(String.format("%sInvalid argument (&o%s&r)&r", OpenSkid.clientName, args.get(1)));
        }
    }

    private void listScripts(ScriptManager manager) {
        List<String> loaded = manager.getScriptNames();
        List<String> files = manager.availableScriptFiles();
        if (loaded.isEmpty() && files.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sNo scripts found (&o./config/OpenSkid/scripts/&r)&r", OpenSkid.clientName));
            return;
        }
        ChatUtil.sendFormatted(String.format("%sScripts:&r", OpenSkid.clientName));
        for (String name : loaded) {
            String state = manager.isEnabled(name) ? "&aon" : "&coff";
            ChatUtil.sendFormatted(String.format("&7»&r &o%s&r [%s&r]", name, state));
        }
        for (String name : files) {
            if (!manager.isLoaded(name)) {
                ChatUtil.sendFormatted(String.format("&7»&r &o%s&r [&7not loaded&r]", name));
            }
        }
    }

    private void sendUsage(ArrayList<String> args) {
        String base = args.isEmpty() ? "script" : args.get(0).toLowerCase(Locale.ROOT);
        ChatUtil.sendFormatted(String.format("%sUsage: .%s &olist&r | .%s &oload&r/&ounload&r/&oreload&r <&oname&r> | .%s &oenable&r/&odisable&r <&oname&r>&r",
                OpenSkid.clientName, base, base, base));
    }
}
