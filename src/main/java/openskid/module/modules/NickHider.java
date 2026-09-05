package openskid.module.modules;

import openskid.enums.ChatColors;
import openskid.module.Module;
import openskid.property.properties.BooleanProperty;
import openskid.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NickHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Pattern SCOREBOARD_DATE = Pattern.compile("§7\\d{1,2}/\\d{1,2}/\\d{2}(?:\\d{2})?\\s+§8.*");
    public final TextProperty protectName = new TextProperty("name", "You");
    public final BooleanProperty scoreboard = new BooleanProperty("scoreboard", true);
    public final BooleanProperty level = new BooleanProperty("level", true);

    public NickHider() {
        super("NickHider", false, true, "Hides your username in chat and scoreboard.");
    }

    public String replaceNick(String input) {
        if (input == null || mc.thePlayer == null) {
            return input;
        }
        if (this.scoreboard.getValue() && SCOREBOARD_DATE.matcher(input).matches()) {
            input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
        }
        String name = mc.thePlayer.getName();
        if (name == null || name.isEmpty()) {
            return input;
        }
        return input.replaceAll(
                Pattern.quote(name), Matcher.quoteReplacement(ChatColors.formatColor(this.protectName.getValue()))
        );
    }
}
