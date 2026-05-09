package com.slize.datarium.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slize.datarium.util.RespackOptsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GuiRespackOpts extends GuiScreen {

    private final GuiScreen parent;
    private final IResourcePack pack;
    private final JsonObject packConfig;
    private final String packPrefix;

    private OptionList list;
    private final List<String> categories = new ArrayList<>();
    private String currentCategory = "";

    private final List<OptionEntry> currentOptions = new ArrayList<>();

    public GuiRespackOpts(GuiScreen parent, IResourcePack pack, JsonObject config) {
        this.parent = parent;
        this.pack = pack;
        this.packConfig = config;
        this.packPrefix = config.has("id") ? config.get("id").getAsString() : "";
    }

    @Override
    public void initGui() {
        this.categories.clear();
        this.buttonList.clear();

        if (this.packConfig.has("conf")) {
            JsonObject conf = this.packConfig.getAsJsonObject("conf");
            for (Map.Entry<String, JsonElement> entry : conf.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    categories.add(entry.getKey());
                } else {
                    if (!categories.contains("General")) {
                        categories.addFirst("General");
                    }
                }
            }
        }

        if (!categories.isEmpty()) {
            if (currentCategory.isEmpty()) {
                currentCategory = categories.getFirst();
            }
        }

        int tabWidth = 100;
        int totalTabsWidth = categories.size() * tabWidth;
        int xOffset = (this.width - totalTabsWidth) / 2;
        if (xOffset < 0) xOffset = 0;

        for (int i = 0; i < categories.size(); i++) {
            String rawCat = categories.get(i);
            String displayCat = formatName(rawCat);

            int btnX = xOffset + (i * tabWidth);
            if (btnX + tabWidth < this.width) {
                GuiButton btn = new GuiButton(100 + i, btnX, 10, tabWidth, 20, displayCat);
                btn.enabled = !rawCat.equals(currentCategory);
                this.buttonList.add(btn);
            }
        }

        this.list = new OptionList(this.mc, this.width, this.height, 40, this.height - 35, 36);
        refreshOptionList();

        // 'Done' button
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 28, I18n.format("gui.done")));
    }

    private String formatName(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] words = name.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])|_");
        return Arrays.stream(words)
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    private void refreshOptionList() {
        currentOptions.clear();
        JsonObject conf = this.packConfig.getAsJsonObject("conf");

        if (currentCategory.equals("General")) {
            for (Map.Entry<String, JsonElement> entry : conf.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    String key = entry.getKey();
                    String fullKey = packPrefix.isEmpty() ? key : packPrefix + "." + key;
                    boolean def = entry.getValue().getAsBoolean();
                    currentOptions.add(new OptionEntry(formatName(key), fullKey, def));
                }
            }
        } else if (conf.has(currentCategory)) {
            addOptionsFrom(conf.getAsJsonObject(currentCategory), currentCategory);
        }
    }

    private void addOptionsFrom(JsonObject obj, String pathSoFar) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                String relativePath = pathSoFar.isEmpty() ? key : pathSoFar + "." + key;
                String fullKey = packPrefix.isEmpty() ? relativePath : packPrefix + "." + relativePath;

                currentOptions.add(new OptionEntry(formatName(key), fullKey, entry.getValue().getAsBoolean()));
            } else if (entry.getValue().isJsonObject()) {
                addOptionsFrom(entry.getValue().getAsJsonObject(), pathSoFar + "." + key);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (RespackOptsManager.hasChanges()) {
                this.mc.refreshResources();
            }
            this.mc.displayGuiScreen(parent);
        } else if (button.id >= 100) {
            int idx = button.id - 100;
            if (idx < categories.size()) {
                this.currentCategory = categories.get(idx);
                this.initGui();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.list.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRenderer, this.pack.getPackName(), this.width / 2, 2, 0xFFFFFF);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.list.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Delegate click to the list first (handles button clicks inside list)
        if (this.list.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.list.mouseReleased(mouseX, mouseY, state)) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    /**
     * Entry implementation using GuiListExtended.IGuiListEntry.
     */
    public class OptionEntry implements GuiListExtended.IGuiListEntry {
        String label;
        String fullKey;
        boolean defaultValue;

        // Buttons
        GuiButton btnValue;
        GuiButton btnReset;

        OptionEntry(String label, String fullKey, boolean defaultValue) {
            this.label = label;
            this.fullKey = fullKey;
            this.defaultValue = defaultValue;
            this.btnValue = new GuiButton(0, 0, 0, 60, 20, "");
            this.btnReset = new GuiButton(1, 0, 0, 40, 20, "Reset");
        }

        void updateState() {
            boolean current = RespackOptsManager.getFlag(fullKey);
            this.btnValue.displayString = current ? TextFormatting.GREEN + "True" : TextFormatting.RED + "False";
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            this.updateState();

            // Draw Label
            GuiRespackOpts.this.drawString(mc.fontRenderer, this.label, x + 5, y + 6, 0xFFFFFF);

            // Calculate button positions
            int right = x + listWidth;

            this.btnValue.x = right - this.btnValue.width;
            this.btnValue.y = y + (slotHeight - this.btnValue.height) / 2;

            boolean currentVal = RespackOptsManager.getFlag(this.fullKey);
            this.btnReset.visible = (currentVal != this.defaultValue);

            if (this.btnReset.visible) {
                this.btnReset.x = this.btnValue.x - this.btnReset.width - 5;
                this.btnReset.y = this.btnValue.y;
                this.btnReset.drawButton(mc, mouseX, mouseY, partialTicks);
            }

            this.btnValue.drawButton(mc, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            // Check Value Button
            if (this.btnValue.mousePressed(mc, mouseX, mouseY)) {
                boolean current = RespackOptsManager.getFlag(this.fullKey);
                RespackOptsManager.setUserFlag(this.fullKey, !current);
                this.updateState();
                this.btnValue.playPressSound(mc.getSoundHandler());
                return true;
            }

            // Check Reset Button
            if (this.btnReset.visible && this.btnReset.mousePressed(mc, mouseX, mouseY)) {
                RespackOptsManager.removeOverride(this.fullKey, this.defaultValue);
                this.updateState();
                this.btnReset.playPressSound(mc.getSoundHandler());
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            this.btnValue.mouseReleased(mouseX, mouseY);
            this.btnReset.mouseReleased(mouseX, mouseY);
        }
    }

    class OptionList extends GuiListExtended {

        public OptionList(Minecraft mcIn, int width, int height, int top, int bottom, int slotHeight) {
            super(mcIn, width, height, top, bottom, slotHeight);
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return currentOptions.get(index);
        }

        @Override
        protected int getSize() {
            return currentOptions.size();
        }

        @Override
        protected int getScrollBarX() {
            return this.width / 2 + 124;
        }

        @Override
        public int getListWidth() {
            return 240;
        }
    }
}