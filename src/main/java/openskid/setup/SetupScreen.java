package openskid.setup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import openskid.ui.impl.gui.BackgroundRenderer;
import openskid.ui.impl.gui.ModernGuiButton;
import openskid.font.FontProcess;
import openskid.util.RenderUtil;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetupScreen extends GuiScreen {
    private static final int ROW_H = 30;
    private static final int CARD_W = 440;

    private static final int ACCENT = 0xFF55FFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int CARD_BG = 0xFF141416;
    private static final int CARD_HOVER = 0xFF1E1E22;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<Row> rows = new ArrayList<>();
    private final Set<SetupEntry> selected = new HashSet<>();
    private final Map<SetupEntry, Long> progress = new HashMap<>();
    private final Map<SetupEntry, Long> totals = new HashMap<>();

    private boolean returnToMenu;
    private int scroll;
    private String footer = "";
    private boolean finished;
    private int autoTotal;
    private int autoDone;

    private GuiButton downloadButton;
    private GuiButton skipButton;
    private GuiButton selectAllButton;
    private GuiButton rescanButton;

    private static final class Row {
        final SetupEntry entry;
        String status;
        boolean busy;

        Row(SetupEntry entry, String status) {
            this.entry = entry;
            this.status = status;
        }
    }

    public SetupScreen() {
        rescan();
        List<SetupEntry> autos = SetupCatalog.autoEntries();
        autoTotal = autos.size();
        for (final SetupEntry entry : autos) {
            if (!SetupScanner.isInstalled(entry) && SetupDownloader.hasLink(entry)) {
                footer = "Installing " + entry.displayName() + " in the background.";
                SetupDownloader.downloadAsync(entry, listener(entry, true));
            } else {
                autoDone++;
            }
        }
    }

    public void setReturnToMenu(boolean returnToMenu) {
        this.returnToMenu = returnToMenu;
    }

    private void rescan() {
        rows.clear();
        selected.clear();
        for (SetupEntry entry : SetupCatalog.mods()) {
            if (entry.auto) {
                continue;
            }
            rows.add(new Row(entry, SetupScanner.isInstalled(entry) ? "installed" : "missing"));
            if (!SetupScanner.isInstalled(entry) && SetupDownloader.hasLink(entry)) {
                selected.add(entry);
            }
        }
        for (SetupEntry entry : SetupCatalog.packs()) {
            if (entry.auto) {
                continue;
            }
            rows.add(new Row(entry, SetupScanner.isInstalled(entry) ? "installed" : "missing"));
            if (!SetupScanner.isInstalled(entry) && SetupDownloader.hasLink(entry)) {
                selected.add(entry);
            }
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int by = height - 44;
        if (!finished) {
            downloadButton = new ModernGuiButton(1, cx - 220, by, 104, 20, "Download");
            selectAllButton = new ModernGuiButton(2, cx - 110, by, 104, 20, "Select all");
            rescanButton = new ModernGuiButton(3, cx, by, 104, 20, "Rescan");
            skipButton = new ModernGuiButton(4, cx + 110, by, 104, 20, "Skip");
            buttonList.add(downloadButton);
            buttonList.add(selectAllButton);
            buttonList.add(rescanButton);
            buttonList.add(skipButton);
        } else {
            buttonList.add(new ModernGuiButton(5, cx - 110, by, 104, 20, "Restart now"));
            buttonList.add(new ModernGuiButton(6, cx, by, 104, 20, "Later"));
        }
        refreshButtons();
    }

    private void refreshButtons() {
        if (finished || downloadButton == null) {
            return;
        }
        boolean anyBusy = false;
        for (Row row : rows) {
            if (row.busy) {
                anyBusy = true;
                break;
            }
        }
        downloadButton.enabled = !selected.isEmpty() && !anyBusy;
        selectAllButton.enabled = !anyBusy;
        rescanButton.enabled = !anyBusy;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            startSelected();
        } else if (button.id == 2) {
            for (Row row : rows) {
                if (!row.busy && row.status.equals("missing") && SetupDownloader.hasLink(row.entry)) {
                    selected.add(row.entry);
                }
            }
        } else if (button.id == 3) {
            rescan();
            progress.clear();
            totals.clear();
            footer = "Rescanned. Drop files in yourself and hit Rescan anytime.";
            checkFinished();
            refreshButtons();
        } else if (button.id == 4) {
            closeDone();
        } else if (button.id == 5) {
            SetupState.markDone();
            mc.shutdown();
        } else if (button.id == 6) {
            closeDone();
        }
    }

    private void closeDone() {
        SetupState.markDone();
        if (returnToMenu) {
            try {
                mc.displayGuiScreen(new openskid.ui.impl.mainmenu.OpenSkidMainMenu());
            } catch (Exception e) {
                mc.displayGuiScreen(null);
            }
        } else {
            mc.displayGuiScreen(null);
        }
    }

    private void toggle(Row row) {
        if (row.busy || row.status.equals("installed")) {
            return;
        }
        if (!SetupDownloader.hasLink(row.entry)) {
            openInBrowser(row.entry);
            return;
        }
        if (selected.contains(row.entry)) {
            selected.remove(row.entry);
        } else {
            selected.add(row.entry);
        }
    }

    private void openInBrowser(SetupEntry entry) {
        if (!SetupDownloader.hasLink(entry)) {
            footer = "No link yet for " + entry.displayName() + ".";
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(entry.url));
            footer = "Opened in browser. Drop the file in and hit Rescan.";
        } catch (Exception e) {
            footer = "Could not open browser.";
        }
    }

    private void startSelected() {
        List<SetupEntry> queue = new ArrayList<>(selected);
        selected.clear();
        for (Row row : rows) {
            if (queue.contains(row.entry)) {
                row.busy = true;
                row.status = "starting";
                SetupDownloader.downloadAsync(row.entry, listener(row.entry, false));
            }
        }
        refreshButtons();
    }

    private SetupDownloader.ProgressListener listener(final SetupEntry entry, final boolean silent) {
        return new SetupDownloader.ProgressListener() {
            @Override
            public void onProgress(final long downloaded, final long total) {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        progress.put(entry, downloaded);
                        totals.put(entry, total);
                        Row row = find(entry);
                        if (row != null && !silent) {
                            row.status = "downloading";
                        }
                    }
                });
            }

            @Override
            public void onDone(final File file) {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        onEntryDone(entry, silent);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        Row row = find(entry);
                        if (row != null && !silent) {
                            row.busy = false;
                            row.status = "failed";
                            selected.add(entry);
                        }
                        footer = entry.displayName() + " failed: " + message + ". Click the row to retry.";
                        refreshButtons();
                    }
                });
            }
        };
    }

    private void onEntryDone(SetupEntry entry, boolean silent) {
        if (silent) {
            autoDone++;
            if (autoDone >= autoTotal) {
                footer = "Background installs done.";
            }
            return;
        }
        Row row = find(entry);
        if (row != null) {
            row.busy = false;
            row.status = "installed";
            progress.remove(entry);
            totals.remove(entry);
        }
        if (entry.section == SetupEntry.Section.PACKS) {
            activatePack(entry.fileName);
        }
        footer = entry.displayName() + " installed.";
        checkFinished();
        refreshButtons();
    }

    private void checkFinished() {
        for (Row row : rows) {
            if (row.busy) {
                return;
            }
        }
        for (Row row : rows) {
            if (row.status.equals("missing") && SetupDownloader.hasLink(row.entry)) {
                return;
            }
        }
        finish();
    }

    private void finish() {
        finished = true;
        SetupState.markDone();
        footer = "All done. Mods need a restart to load.";
        initGui();
    }

    private Row find(SetupEntry entry) {
        for (Row row : rows) {
            if (row.entry == entry) {
                return row;
            }
        }
        return null;
    }

    private void activatePack(String fileName) {
        try {
            File file = new File(SetupScanner.packsDir(), fileName);
            if (!file.isFile() || file.length() < 4096) {
                return;
            }
            List<String> packs = mc.gameSettings.resourcePacks;
            String key = "file/" + fileName;
            if (!packs.contains(key)) {
                packs.add(0, key);
            }
            mc.gameSettings.saveOptions();
            mc.refreshResources();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? ROW_H : -ROW_H;
            if (scroll < 0) {
                scroll = 0;
            }
            int maxScroll = Math.max(0, contentHeight() - (height - 150));
            if (scroll > maxScroll) {
                scroll = maxScroll;
            }
        }
    }

    private int contentHeight() {
        int height = 0;
        SetupEntry.Section last = null;
        for (Row row : rows) {
            if (last != row.entry.section) {
                height += 26;
                last = row.entry.section;
            }
            height += ROW_H;
        }
        return height;
    }

    private int rowTop(int index) {
        int y = 86 - scroll;
        SetupEntry.Section last = null;
        for (int i = 0; i <= index && i < rows.size(); i++) {
            if (last != rows.get(i).entry.section) {
                y += 26;
                last = rows.get(i).entry.section;
            }
            if (i == index) {
                return y;
            }
            y += ROW_H;
        }
        return y;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        try {
            super.mouseClicked(mouseX, mouseY, button);
        } catch (Exception ignored) {
        }
        if (button != 0) {
            return;
        }
        int cx = width / 2;
        for (int i = 0; i < rows.size(); i++) {
            int y = rowTop(i);
            if (mouseX >= cx - CARD_W / 2 && mouseX <= cx + CARD_W / 2 && mouseY >= y && mouseY <= y + ROW_H - 4) {
                Row row = rows.get(i);
                toggle(row);
                if (row.status.startsWith("failed")) {
                    row.busy = true;
                    row.status = "starting";
                    selected.remove(row.entry);
                    SetupDownloader.downloadAsync(row.entry, listener(row.entry, false));
                    refreshButtons();
                }
                break;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        try {
            BackgroundRenderer.draw(width, height);
        } catch (Exception e) {
            drawDefaultBackground();
        }
        drawRect(0, 0, width, height, 0x99000000);

        FontProcess.getScaledFont("sans", 3.0f).drawCenteredString("OpenSkid Setup", width / 2, 22, -1);
        drawCenteredString(fontRendererObj, "Tick what you want. Missing items download to the right folders.",
                width / 2, 52, 0xFFAAAAAA);

        int cx = width / 2;
        SetupEntry.Section lastSection = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (lastSection != row.entry.section) {
                int hy = rowTop(i) - 20;
                if (hy > 60 && hy < height - 60) {
                    drawString(fontRendererObj, row.entry.section == SetupEntry.Section.MODS ? "Mods" : "Resource packs",
                            cx - CARD_W / 2, hy, 0xFF55FFFF);
                }
                lastSection = row.entry.section;
            }
            drawRow(row, rowTop(i), mouseX, mouseY, selected.contains(row.entry));
        }
        if (!footer.isEmpty()) {
            drawCenteredString(fontRendererObj, footer, width / 2, height - 58, 0xFFFFFF55);
        }
        super.drawScreen(mouseX, mouseY, ticks);
    }

    private void drawRow(Row row, int y, int mouseX, int mouseY, boolean ticked) {
        if (y < 60 || y > height - 70) {
            return;
        }
        int cx = width / 2;
        int x = cx - CARD_W / 2;
        boolean hovered = mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + ROW_H - 4;
        boolean installed = row.status.equals("installed");
        boolean failed = row.status.startsWith("failed");

        RenderUtil.drawRoundedRect((float) x, (float) y, (float) CARD_W, (float) (ROW_H - 4), 4.0f,
                hovered && !installed ? CARD_HOVER : CARD_BG, true, true, true, true);
        int barColor = installed ? GREEN : ticked ? ACCENT : failed ? RED : 0xFF333336;
        RenderUtil.drawRoundedRect((float) x, (float) y, 3.0f, (float) (ROW_H - 4), 1.0f,
                barColor, true, true, true, true);

        int checkColor = installed || ticked ? GREEN : 0xFF555558;
        RenderUtil.drawRoundedRectOutline((float) (x + 12), (float) (y + 7), 12.0f, 12.0f, 3.0f, 1.0f,
                checkColor, true, true, true, true);
        if (ticked || installed) {
            drawString(fontRendererObj, "X", x + 15, y + 8, 0xFF55FF55);
        }

        int nameColor = installed ? 0xFF55FF55 : failed ? 0xFFFF5555 : 0xFFFFFFFF;
        String label = row.entry.displayName();
        if (!SetupDownloader.hasLink(row.entry) && !installed) {
            label += " (no link yet)";
        }
        drawString(fontRendererObj, label, x + 30, y + 4, nameColor);
        drawString(fontRendererObj, row.entry.blurb, x + 30, y + 15, 0xFF777777);

        String status = row.status;
        if (status.equals("failed")) {
            status = "failed, click to retry";
        }
        int statusColor = installed ? 0xFF55FF55 : failed ? 0xFFFF5555 : row.busy ? ACCENT : 0xFFAAAAAA;
        drawString(fontRendererObj, status, x + CARD_W - fontRendererObj.getStringWidth(status) - 10, y + 4, statusColor);

        Long done = progress.get(row.entry);
        Long total = totals.get(row.entry);
        if (done != null) {
            float fraction = total != null && total > 0 ? Math.min(1.0f, (float) (done / (double) total)) : -1.0f;
            if (fraction >= 0) {
                RenderUtil.drawRoundedRect((float) (x + 30), (float) (y + ROW_H - 8),
                        (float) ((CARD_W - 40) * fraction), 2.0f, 1.0f, ACCENT, true, true, true, true);
            } else {
                drawString(fontRendererObj, (done / 1024) + "KB", x + CARD_W - 60, y + 15, ACCENT);
            }
        }
    }

    @Override
    protected void keyTyped(char typed, int keyCode) {
        if (keyCode == 1) {
            if (finished) {
                return;
            }
            boolean anyBusy = false;
            for (Row row : rows) {
                if (row.busy) {
                    anyBusy = true;
                    break;
                }
            }
            if (!anyBusy) {
                closeDone();
            }
        }
    }
}
