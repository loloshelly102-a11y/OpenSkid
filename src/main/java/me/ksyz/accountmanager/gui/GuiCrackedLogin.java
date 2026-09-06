package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiCrackedLogin extends GuiScreen {
    private final GuiScreen previousScreen;

    private String status = "&fEnter a username to log in offline (cracked).&r";
    private GuiTextField usernameField;
    private int nameLength = 8;

    private static final String NAME_FIRST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NAME_REST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final java.util.Random NAME_RANDOM = new java.util.Random();

    public GuiCrackedLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(mc);

        usernameField = new GuiTextField(
                1, mc.fontRendererObj, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2, 200, 20
        );
        // Minecraft usernames are at most 16 characters.
        usernameField.setMaxStringLength(16);
        usernameField.setFocused(true);

        buttonList.add(new GuiButton(
                998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Login"
        ));
        buttonList.add(new GuiButton(
                997, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 54, 98, 20, "Random"
        ));
        buttonList.add(new GuiButton(
                996, sr.getScaledWidth() / 2 + 2, sr.getScaledHeight() / 2 + 54, 48, 20, "<"
        ));
        buttonList.add(new GuiButton(
                995, sr.getScaledWidth() / 2 + 54, sr.getScaledHeight() / 2 + 54, 48, 20, ">"
        ));
    }

    private void randomizeName() {
        StringBuilder name = new StringBuilder(nameLength);
        name.append(NAME_FIRST.charAt(NAME_RANDOM.nextInt(NAME_FIRST.length())));
        for (int i = 1; i < nameLength; i++) {
            name.append(NAME_REST.charAt(NAME_RANDOM.nextInt(NAME_REST.length())));
        }
        usernameField.setText(name.toString());
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawCenteredString(
                fontRendererObj, "Cracked Login",
                width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2 - 14, 11184810
        );
        if (status != null) {
            drawCenteredString(
                    fontRendererObj, TextFormatting.translate(status),
                    width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - 14, -1
            );
        }
        usernameField.drawTextBox();
        drawCenteredString(fontRendererObj, String.valueOf(nameLength),
                width / 2 + 27, height / 2 + 60, 0xFFAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }

        if (button.id == 997) {
            randomizeName();
            return;
        }

        if (button.id == 996) {
            if (nameLength > 3) {
                nameLength--;
            }
            return;
        }

        if (button.id == 995) {
            if (nameLength < 16) {
                nameLength++;
            }
            return;
        }

        if (button.id == 998) {            String username = usernameField.getText().trim();

            // Standard Minecraft username rules: 3-16 chars, letters/digits/underscore.
            if (!username.matches("^\\w{3,16}$")) {
                status = "&cInvalid username (3-16 letters, digits or _).&r";
                return;
            }

            // Re-use an existing cracked entry instead of creating duplicates.
            boolean exists = false;
            for (Account account : AccountManager.accounts) {
                if (account.isCracked() && account.getUsername().equalsIgnoreCase(username)) {
                    username = account.getUsername();
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                AccountManager.accounts.add(Account.cracked(username));
                AccountManager.save();
            }

            Session session = SessionManager.offline(username);
            SessionManager.set(session);

            mc.displayGuiScreen(new GuiAccountManager(
                    previousScreen,
                    new Notification(TextFormatting.translate(String.format(
                            "&aSuccessful login! (%s)&r", session.getUsername()
                    )), 5000L)
            ));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        usernameField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_RETURN) {
            actionPerformed(buttonList.get(0));
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previousScreen);
        }
    }
}
