package openskid.command.commands;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import openskid.OpenSkid;
import openskid.command.Command;
import openskid.enums.ChatColors;
import openskid.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

public class DenickCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public DenickCommand() {
        super(new ArrayList<>(Collections.singletonList("denick")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted(String.format("%sUsage: .%s <&oname&r>&r", OpenSkid.clientName, args.get(0).toLowerCase(Locale.ROOT)));
        } else {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(ChatColors.formatColor(args.get(1)));
            if (playerInfo != null) {
                GameProfile gameProfile = playerInfo.getGameProfile();
                Property property = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
                if (property != null) {
                    final String argName = args.get(1);
                    String name;
                    String uuid;
                    try {
                        String code = new String(Base64.getDecoder().decode(property.getValue().getBytes(StandardCharsets.UTF_8)));
                        name = code.contains("profileName\" : \"") ? code.split("profileName\" : \"")[1].split("\"")[0] : "?";
                        uuid = code.contains("profileId\" : \"") ? code.split("profileId\" : \"")[1].split("\"")[0] : "?";
                    } catch (Exception e) {
                        ChatUtil.sendRaw(
                                String.format(
                                        ChatColors.formatColor("%sCould not decode textures for &o%s&r"),
                                        ChatColors.formatColor(OpenSkid.clientName),
                                        argName
                                )
                        );
                        return;
                    }
                    ChatUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%s%s&r -> %s (&o%s&r)&r"),
                                    ChatColors.formatColor(OpenSkid.clientName),
                                    gameProfile.getName().replace("§", "&"),
                                    name,
                                    uuid
                            )
                    );
                    if (!uuid.isEmpty() && !uuid.equals("?")) {
                        try {
                            if (!GraphicsEnvironment.isHeadless()) {
                                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uuid), null);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    ChatUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%sNo textures for entity with name &o%s&r"),
                                    ChatColors.formatColor(OpenSkid.clientName),
                                    args.get(1)
                            )
                    );
                }
            } else {
                ChatUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%sNo entity with name &o%s&r"),
                                ChatColors.formatColor(OpenSkid.clientName),
                                args.get(1)
                        )
                );
            }
        }
    }
}
