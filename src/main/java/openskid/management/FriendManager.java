package openskid.management;

import openskid.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class FriendManager extends PlayerFileManager {
    public FriendManager() {
        super(new File("./config/OpenSkid/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}
