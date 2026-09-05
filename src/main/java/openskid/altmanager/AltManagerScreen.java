package openskid.altmanager;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Adapted for OpenSkid. Layout idea follows donor AltManagerGui and
// CrackedLoginGui (list plus add, delete, login, random buttons) but this is
// a plain vanilla GuiScreen rewritten for MCP names. No donor code pasted.
public class AltManagerScreen extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField nameField;
    private GuiButton loginBtn;
    private GuiButton refreshBtn;
    private GuiButton copyCodeBtn;
    private volatile String status = "Idle. Cracked logins work offline. Microsoft uses device code.";
    private volatile String pendingCode;
    private volatile String pendingUri;
    private volatile boolean busy;
    private int selected = -1;
    private int scroll;
    private final Random random = new Random();

    public AltManagerScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        AltStore.load();
        this.buttonList.clear();
        int cx = this.width / 2;
        int by = this.height - 88;
        nameField = new GuiTextField(100, this.fontRendererObj, cx - 200, by - 28, 200, 20);
        nameField.setMaxStringLength(16);
        this.buttonList.add(new GuiButton(0, cx - 200, by, 96, 20, "Add cracked"));
        this.buttonList.add(new GuiButton(1, cx - 100, by, 96, 20, "MS login"));
        loginBtn = new GuiButton(2, cx + 4, by, 96, 20, "Login sel.");
        this.buttonList.add(loginBtn);
        this.buttonList.add(new GuiButton(3, cx + 104, by, 96, 20, "Random"));
        refreshBtn = new GuiButton(4, cx - 200, by + 24, 96, 20, "Use refresh");
        this.buttonList.add(refreshBtn);
        this.buttonList.add(new GuiButton(5, cx - 100, by + 24, 96, 20, "Delete"));
        copyCodeBtn = new GuiButton(6, cx + 4, by + 24, 96, 20, "Copy MS code");
        this.buttonList.add(copyCodeBtn);
        this.buttonList.add(new GuiButton(7, cx + 104, by + 24, 96, 20, parent == null ? "Close" : "Back"));
        refreshButtons();
    }

    private void refreshButtons() {
        List<Alt> alts = AltStore.getAlts();
        boolean hasSel = selected >= 0 && selected < alts.size();
        Alt sel = hasSel ? alts.get(selected) : null;
        loginBtn.enabled = hasSel && !busy;
        refreshBtn.enabled = hasSel && sel != null && !sel.isCracked() && sel.hasRefreshToken() && !busy;
        copyCodeBtn.enabled = pendingCode != null && !pendingCode.isEmpty();
    }

    private void setStatus(String s) {
        status = s;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, "Alt Manager", this.width / 2, 8, 0xFFFFFF);
        drawString(this.fontRendererObj, "Current: " + SessionSwap.currentName(), 8, 8, 0xAAAAAA);
        List<String> lines = splitStatus(status, 90);
        for (int i = 0; i < lines.size() && i < 3; i++) {
            drawString(this.fontRendererObj, lines.get(i), 8, 20 + i * 10, i == 0 ? 0xCCCCCC : 0x999999);
        }
        int top = 52;
        int bottom = this.height - 122;
        drawRect(8, top - 4, this.width - 8, bottom + 4, 0x55000000);
        List<Alt> alts = new ArrayList<Alt>(AltStore.getAlts());
        String current = SessionSwap.currentName();
        int rowH = 22;
        int visible = Math.max(1, (bottom - top) / rowH);
        if (scroll > Math.max(0, alts.size() - visible)) {
            scroll = Math.max(0, alts.size() - visible);
        }
        if (scroll < 0) {
            scroll = 0;
        }
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= alts.size()) {
                break;
            }
            Alt a = alts.get(idx);
            int y = top + i * rowH;
            boolean isSel = idx == selected;
            if (isSel) {
                drawRect(10, y, this.width - 10, y + rowH - 2, 0x6644AAFF);
            }
            int color = a.isFlagged() ? 0xFF5555 : (a.isCracked() ? 0xDDDDDD : 0x55FF55);
            drawString(this.fontRendererObj, a.displayLabel(current), 16, y + 6, color);
        }
        if (alts.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "No alts saved. Type a name below, then Add cracked.", this.width / 2, top + 10, 0x999999);
        }
        if (nameField != null) {
            nameField.drawTextBox();
            drawString(this.fontRendererObj, "Cracked name:", this.width / 2 - 200, this.height - 88 - 26, 0x999999);
        }
        super.drawScreen(mouseX, mouseY, ticks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException {
        super.mouseClicked(mouseX, mouseY, btn);
        if (nameField != null) {
            nameField.mouseClicked(mouseX, mouseY, btn);
        }
        int top = 52;
        int bottom = this.height - 122;
        int rowH = 22;
        if (mouseX > 10 && mouseX < this.width - 10 && mouseY >= top && mouseY < bottom) {
            int idx = scroll + (mouseY - top) / rowH;
            if (idx >= 0 && idx < AltStore.getAlts().size()) {
                selected = idx;
                refreshButtons();
            }
        }
    }

    @Override
    protected void keyTyped(char ch, int key) throws IOException {
        if (nameField != null && nameField.isFocused()) {
            nameField.textboxKeyTyped(ch, key);
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                addCrackedFromField();
            }
            return;
        }
        super.keyTyped(ch, key);
    }

    @Override
    public void updateScreen() {
        if (nameField != null) {
            nameField.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        AltStore.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Integer.signum(wheel);
            if (scroll < 0) {
                scroll = 0;
            }
            int max = Math.max(0, AltStore.getAlts().size() - 1);
            if (scroll > max) {
                scroll = max;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            addCrackedFromField();
        } else if (button.id == 1) {
            startMicrosoftLogin();
        } else if (button.id == 2) {
            loginSelected();
        } else if (button.id == 3) {
            randomLogin();
        } else if (button.id == 4) {
            refreshSelected();
        } else if (button.id == 5) {
            deleteSelected();
        } else if (button.id == 6) {
            copyPendingCode();
        } else if (button.id == 7) {
            AltStore.save();
            this.mc.displayGuiScreen(parent);
        }
    }

    private void addCrackedFromField() {
        String name = nameField == null ? "" : nameField.getText().trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_]{3,16}")) {
            setStatus("Cracked name must be 3-16 chars: letters, digits, underscore.");
            return;
        }
        if (BanTracker.isFlagged(name)) {
            setStatus("That name is flagged banned/locked here. Not added.");
            return;
        }
        Alt alt = AltStore.addCracked(name);
        if (alt == null) {
            setStatus("That cracked alt is already in the list.");
            return;
        }
        if (!SessionSwap.setCracked(name)) {
            setStatus("Added " + name + " but session swap failed. Try again.");
            return;
        }
        AltStore.touchUsed(alt);
        selected = AltStore.getAlts().indexOf(alt);
        setStatus("Logged in (offline) as " + name + ". Works on cracked servers only.");
        refreshButtons();
    }

    private void loginSelected() {
        List<Alt> alts = AltStore.getAlts();
        if (selected < 0 || selected >= alts.size()) {
            setStatus("Select an alt from the list first.");
            return;
        }
        final Alt alt = alts.get(selected);
        if (BanTracker.shouldSkip(alt)) {
            AltStore.save();
            setStatus(alt.getName() + " is flagged " + (alt.isBanned() ? "banned" : "locked") + ". Skipped, no retry.");
            refreshButtons();
            return;
        }
        if (alt.isCracked()) {
            if (!SessionSwap.setCracked(alt.getName())) {
                setStatus("Session swap failed for " + alt.getName() + ".");
                return;
            }
            AltStore.touchUsed(alt);
            setStatus("Logged in (offline) as " + alt.getName() + ".");
            return;
        }
        refreshSelected();
    }

    private void refreshSelected() {
        List<Alt> alts = AltStore.getAlts();
        if (selected < 0 || selected >= alts.size()) {
            setStatus("Select a Microsoft alt first.");
            return;
        }
        final Alt alt = alts.get(selected);
        if (alt.isCracked() || !alt.hasRefreshToken()) {
            setStatus("Selected alt has no saved Microsoft login. Use MS login for a fresh code.");
            return;
        }
        if (BanTracker.shouldSkip(alt)) {
            setStatus(alt.getName() + " is flagged. Skipped, no retry.");
            return;
        }
        if (busy) {
            return;
        }
        busy = true;
        refreshButtons();
        setStatus("Refreshing Microsoft login for " + alt.getName() + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MicrosoftAuth.Result r = MicrosoftAuth.loginWithRefreshToken(alt.getRefreshToken());
                    Alt updated = AltStore.upsertMicrosoft(r.username, r.uuid, r.mcToken, r.refreshToken);
                    if (!SessionSwap.setMicrosoft(r.username, r.uuid, r.mcToken)) {
                        setStatus("Microsoft auth passed but session swap failed.");
                    } else {
                        AltStore.touchUsed(updated);
                        setStatus("Logged in as " + r.username + ".");
                    }
                } catch (MicrosoftAuth.AuthException e) {
                    boolean flagged = BanTracker.noteLoginFailure(alt.getName(), e.getMessage());
                    setStatus("Microsoft login failed: " + e.getMessage() + (flagged ? " Flagged, will not auto-retry." : ""));
                } finally {
                    busy = false;
                    refreshButtons();
                }
            }
        }, "OpenSkid-MS-Refresh").start();
    }

    private void startMicrosoftLogin() {
        if (busy) {
            return;
        }
        busy = true;
        refreshButtons();
        setStatus("Contacting Microsoft for a device code...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MicrosoftAuth.DeviceCode dc = MicrosoftAuth.requestDeviceCode();
                    pendingCode = dc.userCode;
                    pendingUri = dc.verificationUri;
                    setStatus("In your browser open " + dc.verificationUri + " and enter code " + dc.userCode + ". Waiting...");
                    refreshButtons();
                    MicrosoftAuth.Result r = MicrosoftAuth.loginWithDeviceCode(dc);
                    Alt alt = AltStore.upsertMicrosoft(r.username, r.uuid, r.mcToken, r.refreshToken);
                    if (!SessionSwap.setMicrosoft(r.username, r.uuid, r.mcToken)) {
                        setStatus("Microsoft auth passed but session swap failed.");
                    } else {
                        AltStore.touchUsed(alt);
                        selected = AltStore.getAlts().indexOf(alt);
                        setStatus("Logged in as " + r.username + ".");
                    }
                } catch (MicrosoftAuth.AuthException e) {
                    setStatus("Microsoft login failed: " + e.getMessage());
                } finally {
                    pendingCode = null;
                    pendingUri = null;
                    busy = false;
                    refreshButtons();
                }
            }
        }, "OpenSkid-MS-DeviceCode").start();
    }

    private void randomLogin() {
        List<Alt> alts = AltStore.getAlts();
        if (alts.isEmpty()) {
            setStatus("No alts saved yet.");
            return;
        }
        List<Integer> usable = new ArrayList<Integer>();
        for (int i = 0; i < alts.size(); i++) {
            if (!BanTracker.shouldSkip(alts.get(i))) {
                usable.add(i);
            }
        }
        if (usable.isEmpty()) {
            setStatus("Every alt is flagged banned/locked. Nothing to pick.");
            return;
        }
        selected = usable.get(random.nextInt(usable.size()));
        Alt alt = alts.get(selected);
        if (alt.isCracked()) {
            if (!SessionSwap.setCracked(alt.getName())) {
                setStatus("Session swap failed for " + alt.getName() + ".");
            } else {
                AltStore.touchUsed(alt);
                setStatus("Random pick: logged in (offline) as " + alt.getName() + ".");
            }
            refreshButtons();
        } else {
            setStatus("Random pick: " + alt.getName() + ". Refreshing its Microsoft login...");
            refreshButtons();
            refreshSelected();
        }
    }

    private void deleteSelected() {
        List<Alt> alts = AltStore.getAlts();
        if (selected < 0 || selected >= alts.size()) {
            setStatus("Select an alt to delete first.");
            return;
        }
        Alt alt = alts.get(selected);
        AltStore.remove(alt);
        selected = -1;
        setStatus("Deleted " + alt.getName() + ".");
        refreshButtons();
    }

    private void copyPendingCode() {
        if (pendingCode == null || pendingCode.isEmpty()) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pendingCode), null);
            setStatus("Copied MS code " + pendingCode + ". Open " + pendingUri + " in your browser.");
        } catch (Exception e) {
            setStatus("Code is " + pendingCode + ". Open " + pendingUri + " in your browser.");
        }
    }

    private static List<String> splitStatus(String s, int max) {
        List<String> out = new ArrayList<String>();
        if (s == null) {
            return out;
        }
        String rest = s;
        while (rest.length() > max) {
            out.add(rest.substring(0, max));
            rest = rest.substring(max);
        }
        out.add(rest);
        return out;
    }
}
