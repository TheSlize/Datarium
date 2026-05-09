package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.util.*;

public class CEMDebugSystem {
    private static final List<String> availableParts = new ArrayList<>();
    private static String selectedPart = null;
    private static int selectedIndex = -1;

    // Toggle state
    public static boolean enabled = false;

    private static boolean commaDown = false;
    private static boolean periodDown = false;
    private static boolean semicolonDown = false;

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
        boolean semicolon = Keyboard.isKeyDown(Keyboard.KEY_SEMICOLON);
        if (semicolon && !semicolonDown) {
            enabled = !enabled;
            printStatus();
        }
        semicolonDown = semicolon;

        if (!enabled) return;

        boolean comma = Keyboard.isKeyDown(Keyboard.KEY_COMMA);
        boolean period = Keyboard.isKeyDown(Keyboard.KEY_PERIOD);

        if (comma && !commaDown) {
            cycle(-1);
        }
        if (period && !periodDown) {
            cycle(1);
        }

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
        //DatariumMain.LOGGER.info("[CEM] Debug System: " + status);

        if (Minecraft.getMinecraft().player != null) {
            TextFormatting color = enabled ? TextFormatting.GREEN : TextFormatting.RED;
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    TextFormatting.GOLD + "[CEM] " + TextFormatting.RESET + "Debug: " + color + status
            ));
        }
    }

    public static boolean isSelected(String partName) {
        return enabled && partName != null && partName.equals(selectedPart);
    }
}