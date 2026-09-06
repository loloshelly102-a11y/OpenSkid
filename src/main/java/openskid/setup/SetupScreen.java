package openskid.setup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
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
    private static final int ROW_H = 22;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<Row> rows = new ArrayList<>();
    private final Set<SetupEntry> selected = new HashSet<>();
    private final Map<SetupEntry, Long> progress = new HashMap<>();
    private final Map<SetupEntry, Long> totals = new HashMap<>();

    private int scroll;
    private String footer = "";
    private boolean finished;
    private int autoTotal;
    private int autoDone;

    private GuiButton downloadButton;
    private GuiButton skipButton;
    private GuiButton selectAllButton;
    private GuiButton rescanButton;
    private GuiButton restartButton;
    private GuiButton laterButton;

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

    private void rescan() {
        rows.clear();
        selected.clear();
        for (SetupEntry entry : SetupCatalog.mods()) {
            if (entry.auto) {
                continue;
            }
            Row row = new Row(entry, SetupScanner.isInstalled(entry) ? "installed" : "missing");
            rows.add(row);
            if (!SetupScanner.isInstalled(entry) && SetupDownloader.hasLink(entry)) {
                selected.add(entry);
            }
        }
        for (SetupEntry entry : SetupCatalog.packs()) {
            if (entry.auto) {
                continue;
            }
            Row row = new Row(entry, SetupScanner.isInstalled(entry) ? "installed" : "missing");
            rows.add(row);
            if (!SetupScanner.isInstalled(entry) && SetupDownloader.hasLink(entry)) {
                selected.add(entry);
            }
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int by = height - 48;
        if (!finished) {
            downloadButton = new GuiButton(1, cx - 220, by, 100, 20, "Download");
            selectAllButton = new GuiButton(2, cx - 110, by, 100, 20, "Select all");
            rescanButton = new GuiButton(3, cx, by, 100, 20, "Rescan");
            skipButton = new GuiButton(4, cx + 110, by, 100, 20, "Skip");
            buttonList.add(downloadButton);
            buttonList.add(selectAllButton);
            buttonList.add(rescanButton);
            buttonList.add(skipButton);
        } else {
            restartButton = new GuiButton(5, cx - 110, by, 100, 20, "Restart now");
            laterButton = new GuiButton(6, cx, by, 100, 20, "Later");
            buttonList.add(restartButton);
            buttonList.add(laterButton);
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
            footer = "Rescanned. Drop files in yourself and hit Rescan anytime.";
        } else if (button.id == 4) {
            SetupState.markDone();
            mc.displayGuiScreen(null);
        } else if (button.id == 5) {
            SetupState.markDone();
            mc.shutdown();
        } else if (button.id == 6) {
            SetupState.markDone();
            mc.displayGuiScreen(null);
        } else if (button.id >= 100) {
            int index = button.id - 100;
            if (index >= 0 && index < rows.size()) {
                toggle(rows.get(index));
            }
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
                            row.status = "failed: " + message + " (click to retry)";
                            selected.add(entry);
                        }
                        if (silent) {
                            footer = entry.displayName() + " failed: " + message;
                        } else {
                            footer = entry.displayName() + " failed: " + message;
                        }
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
        int pending = 0;
        for (Row row : rows) {
            if (row.status.equals("missing") && SetupDownloader.hasLink(row.entry)) {
                pending++;
            }
        }
        if (pending == 0) {
            finish();
        }
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
        }
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
        int y = 70 - scroll;
        SetupEntry.Section lastSection = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (lastSection != row.entry.section) {
                y += 18;
                lastSection = row.entry.section;
            }
            if (mouseX >= width / 2 - 200 && mouseX <= width / 2 + 200 && mouseY >= y && mouseY <= y + ROW_H - 4) {
                toggle(row);
                break;
            }
            y += ROW_H;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "OpenSkid first-time setup", width / 2, 20, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Tick what you want. Missing items download to the right folders.", width / 2, 34, 0xAAAAAA);
        int y = 70 - scroll;
        SetupEntry.Section lastSection = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (lastSection != row.entry.section) {
                y += 4;
                drawString(fontRendererObj, row.entry.section == SetupEntry.Section.MODS ? "Mods" : "Resource packs",
                        width / 2 - 200, y, 0x55FFFF);
                y += 14;
                lastSection = row.entry.section;
            }
            drawRow(row, y, selected.contains(row.entry));
            y += ROW_H;
        }
        if (!footer.isEmpty()) {
            drawCenteredString(fontRendererObj, footer, width / 2, height - 62, 0xFFFF55);
        }
        super.drawScreen(mouseX, mouseY, ticks);
    }

    private void drawRow(Row row, int y, boolean ticked) {
        int x = width / 2 - 200;
        boolean clickable = !row.busy && !row.status.equals("installed");
        drawRect(x, y, x + 12, y + 12, 0xFF000000);
        drawRect(x + 1, y + 1, x + 11, y + 11, clickable ? 0xFF222222 : 0xFF111111);
        if (ticked || row.status.equals("installed")) {
            drawString(fontRendererObj, "X", x + 3, y + 2, 0x55FF55);
        }
        int nameColor = row.status.equals("installed") ? 0x55FF55 : row.status.startsWith("failed") ? 0xFF5555 : 0xFFFFFF;
        String label = row.entry.displayName();
        if (!SetupDownloader.hasLink(row.entry) && !row.status.equals("installed")) {
            label += " (no link yet)";
        }
        drawString(fontRendererObj, label, x + 18, y + 2, nameColor);
        String status = row.status;
        Long done = progress.get(row.entry);
        Long total = totals.get(row.entry);
        if (done != null && total != null && total > 0) {
            status += " " + (done * 100 / total) + "%";
        } else if (done != null) {
            status += " " + (done / 1024) + "KB";
        }
        drawString(fontRendererObj, status, width / 2 + 60, y + 2, 0xAAAAAA);
        drawString(fontRendererObj, row.entry.blurb, x + 18, y + 11, 0x777777);
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
                mc.displayGuiScreen(null);
            }
        }
    }
}
