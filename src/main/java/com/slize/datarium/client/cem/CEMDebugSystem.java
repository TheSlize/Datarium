package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public class CEMDebugSystem {
    private static final List<String> availableParts = new ArrayList<>();
    private static String selectedPart = null;
    private static int selectedIndex = -1;
    private static List<String> lastDebugLines = new ArrayList<>();

    public static boolean enabled = false;

    private static boolean commaDown = false;
    private static boolean periodDown = false;
    private static boolean semicolonDown = false;
    private static boolean eKeyDown = false;
    private static boolean jKeyDown = false;
    private static boolean xKeyDown = false;


    public static void updateAvailableParts(Set<String> parts) {
        if (parts == null || parts.isEmpty()) return;

        if (availableParts.size() != parts.size() || !new HashSet<>(availableParts).containsAll(parts)) {
            availableParts.clear();
            availableParts.addAll(parts);
            Collections.sort(availableParts);

            if (selectedPart != null && availableParts.contains(selectedPart)) {
                selectedIndex = availableParts.indexOf(selectedPart);
            } else if (!availableParts.isEmpty()) {
                selectedIndex = 0;
                selectedPart = availableParts.getFirst();
            }
        }
    }

    public static void onGameTick() {
        if (!CEMConfig.debugMode) {
            if (enabled) {
                enabled = false;
                CEMProfiler.stopAndDump();
                printStatus();
            }
            semicolonDown = false;
            return;
        }

        boolean semicolon = Keyboard.isKeyDown(Keyboard.KEY_SEMICOLON);
        if (semicolon && !semicolonDown) {
            enabled = !enabled;
            if (enabled) CEMProfiler.start();
            else CEMProfiler.stopAndDump();
            printStatus();
        }
        semicolonDown = semicolon;

        if (!enabled) return;

        boolean comma = Keyboard.isKeyDown(Keyboard.KEY_COMMA);
        boolean period = Keyboard.isKeyDown(Keyboard.KEY_PERIOD);
        boolean eKey = Keyboard.isKeyDown(Keyboard.KEY_E);

        if (comma && !commaDown) {
            cycle(-1);
        }
        if (period && !periodDown) {
            cycle(1);
        }
        if (eKey && !eKeyDown && selectedPart != null) {
            sendDebugToChat();
        }
        boolean jKey = Keyboard.isKeyDown(Keyboard.KEY_J);
        if (jKey && !jKeyDown) {
            dumpPlayerValues();
        }
        boolean xKey = Keyboard.isKeyDown(Keyboard.KEY_X);
        if (xKey && !xKeyDown && Minecraft.getMinecraft().currentScreen == null) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) exportAll();
            else exportLookedAt();
        }

        xKeyDown = xKey;
        jKeyDown = jKey;
        eKeyDown = eKey;
        commaDown = comma;
        periodDown = period;
    }

    private static void cycle(int dir) {
        if (availableParts.isEmpty()) return;

        selectedIndex += dir;
        if (selectedIndex < 0) selectedIndex = availableParts.size() - 1;
        if (selectedIndex >= availableParts.size()) selectedIndex = 0;

        selectedPart = availableParts.get(selectedIndex);
    }

    private static void printStatus() {
        String status = enabled ? "ENABLED" : "DISABLED";

        if (Minecraft.getMinecraft().player != null) {
            TextFormatting color = enabled ? TextFormatting.GREEN : TextFormatting.RED;
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    TextFormatting.GOLD + "[CEM] " + TextFormatting.RESET + "Debug: " + color + status
            ));
        }
    }

    public static void setLastDebugLines(List<String> lines) {
        lastDebugLines = new ArrayList<>(lines);
    }

    private static void sendDebugToChat() {
        if (lastDebugLines == null || lastDebugLines.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        String fullText = String.join("\n", lastDebugLines);

        GuiScreen.setClipboardString(fullText);

        String preview = lastDebugLines.size() > 3
                ? String.join(" | ", lastDebugLines.subList(0, 3)) + "..."
                : String.join(" | ", lastDebugLines);

        TextComponentString msg = new TextComponentString(
                TextFormatting.GOLD + "[CEM] " + TextFormatting.GREEN + "Copied debug info: "
                        + TextFormatting.AQUA + selectedPart
        );

        TextComponentString hoverBtn = new TextComponentString(
                " " + TextFormatting.GRAY + "[?]"
        );
        hoverBtn.getStyle().setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new TextComponentString(
                        TextFormatting.WHITE + preview
                )
        ));

        msg.appendSibling(hoverBtn);
        mc.player.sendMessage(msg);
    }

    private static void dumpPlayerValues() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        Map<String, Double> values = CEMManager.getEntityState(mc.player).context.dumpValues();
        StringBuilder sb = new StringBuilder("[CEM] value dump for ").append(mc.player.getName()).append(" (").append(values.size()).append(" values):\n");
        for (Map.Entry<String, Double> e : values.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        dumpLookedAtTile(mc, sb);
        DatariumMain.LOGGER.info(sb.toString());
        mc.player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "[CEM] " + TextFormatting.GREEN + "Dumped " + values.size() + " values to log"));
    }

    private static void dumpLookedAtTile(Minecraft mc, StringBuilder sb) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK || mc.world == null) return;
        TileEntity tile = mc.world.getTileEntity(mc.objectMouseOver.getBlockPos());
        if (tile == null) return;
        String modelName = CEMManager.getModelNameForTile(tile);
        sb.append("[CEM] looked-at tile ").append(tile.getClass().getSimpleName()).append(" at ").append(tile.getPos())
                .append(" model=").append(modelName).append('\n');
        CEMRenderState state = CEMManager.peekTileState(tile);
        if (state != null) {
            for (Map.Entry<String, Double> e : state.context.dumpValues().entrySet()) {
                if (e.getKey().startsWith("var") || e.getKey().contains(".")) {
                    sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                }
            }
        }
        CEMModelWrapper wrapper = modelName != null ? CEMManager.peekWrapper(modelName) : null;
        if (wrapper == null) return;
        for (Map.Entry<String, CEMModelRenderer> e : new TreeMap<>(wrapper.getAllParts()).entrySet()) {
            sb.append("  part ").append(e.getKey()).append(": ").append(e.getValue().describe()).append('\n');
        }
    }

    private static void exportAll() {
        Minecraft mc = Minecraft.getMinecraft();
        int count = CEMModelExporter.exportAll();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[CEM] " + TextFormatting.GREEN
                    + "Exported " + count + " models to " + CEMModelExporter.exportDirectory().getAbsolutePath()));
        }
    }

    private static void exportLookedAt() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        String name = null;
        ModelBase model = null;

        Entity target = mc.pointedEntity != null ? mc.pointedEntity : mc.player;
        if (mc.pointedEntity == null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK && mc.world != null) {
            TileEntity tile = mc.world.getTileEntity(mc.objectMouseOver.getBlockPos());
            TileEntitySpecialRenderer<TileEntity> renderer = tile != null ? TileEntityRendererDispatcher.instance.getRenderer(tile) : null;
            if (renderer != null) {
                name = CEMManager.exportNameForTile(tile);
                model = tileModel(tile, CEMGenericRender.modelsOf(renderer));
                target = null;
            }
        }
        if (target != null) {
            Render<Entity> render = mc.getRenderManager().getEntityRenderObject(target);
            if (render instanceof RenderLivingBase<?> living) {
                model = living.getMainModel();
                name = target instanceof AbstractClientPlayer player
                        ? ("slim".equals(player.getSkinType()) ? "player_slim" : "player")
                        : CEMManager.exportNameForEntity(target.getClass());
            }
        }

        File file = name != null && model != null ? CEMModelExporter.export(name, model) : null;
        mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[CEM] " + (file != null
                ? TextFormatting.GREEN + "Exported " + name + " to " + file.getAbsolutePath()
                : TextFormatting.RED + "Nothing exportable here")));
    }

    @Nullable
    private static ModelBase tileModel(TileEntity tile, List<ModelBase> models) {
        if (models.isEmpty()) return null;
        if (tile instanceof TileEntityChest chest) {
            boolean large = chest.adjacentChestXPos != null || chest.adjacentChestZPos != null;
            for (ModelBase model : models) {
                if ((model instanceof ModelLargeChest) == large) return model;
            }
        }
        return models.get(0);
    }

    public static boolean isSelected(String partName) {
        return enabled && partName != null && partName.equals(selectedPart);
    }
}