package com.slize.datarium.client.cet;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CETNbt {
    private static final Pattern QUOTED_GROUPS = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
    private static final Pattern INT_RANGE = Pattern.compile("(\\d+|-\\d+)-(\\d+|-\\d+)");

    private static final Map<Object, NBTTagCompound> CACHE = new IdentityHashMap<>();
    private static final NBTTagCompound MISSING = new NBTTagCompound();
    private static long cacheTick = Long.MIN_VALUE;
    private static volatile Thread computing;

    private CETNbt() {}

    @Nullable
    public static NBTTagCompound of(@Nullable Object subject) {
        if (!(subject instanceof Entity) && !(subject instanceof TileEntity)) return null;
        Minecraft mc = Minecraft.getMinecraft();
        long tick = mc.world != null ? mc.world.getTotalWorldTime() : 0L;
        if (tick != cacheTick) {
            CACHE.clear();
            cacheTick = tick;
        }
        NBTTagCompound tag = CACHE.get(subject);
        if (tag == null) {
            tag = compute(subject);
            CACHE.put(subject, tag != null ? tag : MISSING);
        }
        return tag == MISSING ? null : tag;
    }

    public static boolean isComputing() {
        return computing == Thread.currentThread();
    }

    public static void invalidate() {
        CACHE.clear();
    }

    @Nullable
    private static NBTTagCompound compute(Object subject) {
        try {
            if (subject instanceof TileEntity tile) return tile.writeToNBT(new NBTTagCompound());
            Entity entity = (Entity) subject;
            Entity server = serverEntity(entity);
            Entity source = server != null ? server : entity;
            NBTTagCompound tag;
            computing = Thread.currentThread();
            try {
                tag = CommandBase.entityToNBT(source);
            } finally {
                computing = null;
            }
            if (!tag.hasKey("id")) {
                ResourceLocation id = EntityList.getKey(source);
                if (id != null) tag.setString("id", id.toString());
            }
            if (source instanceof EntityLivingBase living) addModernKeys(tag, living);
            return tag;
        } catch (Exception e) {
            return null;
        }
    }

    private static void addModernKeys(NBTTagCompound tag, EntityLivingBase living) {
        if (!tag.hasKey("equipment")) {
            NBTTagCompound equipment = new NBTTagCompound();
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                ItemStack stack = living.getItemStackFromSlot(slot);
                if (!stack.isEmpty()) equipment.setTag(slot.getName(), stack.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("equipment", equipment);
        }
        BlockPos bed = living instanceof EntityPlayer player && player.isPlayerSleeping() ? player.bedLocation : null;
        if (bed != null) {
            if (!tag.hasKey("SleepingX")) {
                tag.setInteger("SleepingX", bed.getX());
                tag.setInteger("SleepingY", bed.getY());
                tag.setInteger("SleepingZ", bed.getZ());
            }
            if (!tag.hasKey("sleeping_pos")) tag.setTag("sleeping_pos", new NBTTagIntArray(new int[]{bed.getX(), bed.getY(), bed.getZ()}));
        }
    }

    @Nullable
    public static Entity serverEntity(Entity entity) {
        try {
            MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
            if (server == null) return null;
            WorldServer world = server.getWorld(entity.dimension);
            return world != null ? world.getEntityFromUuid(entity.getUniqueID()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Tester {
        private final String path;
        private final String[] steps;
        private final boolean inverts;
        private final boolean wantsBlank;
        private final boolean print;
        private final boolean printAll;
        private final Predicate<NBTBase> tester;

        private Tester(String path, boolean inverts, boolean wantsBlank, boolean print, boolean printAll, Predicate<NBTBase> tester) {
            this.path = path;
            this.steps = path.split("\\.");
            this.inverts = inverts;
            this.wantsBlank = wantsBlank;
            this.print = print;
            this.printAll = printAll;
            this.tester = tester;
        }

        @Nullable
        public static Tester of(String path, String rawInstruction) {
            try {
                String trimmed = rawInstruction.trim().replace("print_raw:", "print:raw:");
                if (path.isBlank() || trimmed.isBlank()) return null;
                boolean printAll = trimmed.startsWith("print_all:");
                String step1 = trimmed.replaceFirst("^print_all:", "");
                boolean print = step1.startsWith("print:");
                String step2 = print ? step1.substring(6) : step1;
                boolean invert = step2.startsWith("!");
                String instruction = invert ? step2.substring(1) : step2;

                if (instruction.startsWith("raw:")) {
                    String raw = instruction.replaceFirst("raw:", "");
                    boolean blank = raw.isBlank();
                    Predicate<String> matcher = blank ? String::isBlank : stringMatcher(raw);
                    if (matcher == null) return null;
                    return new Tester(path, invert, blank, print, printAll, tag -> matcher.test(asString(tag)));
                }
                if (instruction.startsWith("exists:")) {
                    boolean exists = instruction.contains("exists:true");
                    boolean notExists = instruction.contains("exists:false");
                    return new Tester(path, invert, notExists, print, printAll, tag -> exists);
                }
                if (instruction.startsWith("range:")) {
                    int[] range = intRange(instruction.replaceFirst("range:", ""));
                    return new Tester(path, invert, false, print, printAll,
                            tag -> tag instanceof NBTPrimitive number && number.getInt() >= range[0] && number.getInt() <= range[1]);
                }
                Predicate<String> matcher = stringMatcher(instruction);
                if (matcher == null) return null;
                return new Tester(path, invert, false, print, printAll, tag -> matcher.test(
                        tag instanceof NBTPrimitive ? asString(tag).replaceAll("[^\\d.]", "") : asString(tag)));
            } catch (Exception e) {
                return null;
            }
        }

        public String path() {
            return path;
        }

        public boolean test(@Nullable NBTTagCompound root) {
            if (root == null || root.isEmpty()) return false;
            if (printAll) DatariumMain.LOGGER.info("[CET] nbt property [full] print:\n{}", root);
            List<NBTBase> found = find(root, 0);
            boolean pass;
            if (found == null) {
                pass = wantsBlank;
            } else {
                pass = false;
                for (NBTBase tag : found) {
                    if (tag != null && tester.test(tag)) {
                        pass = true;
                        break;
                    }
                }
            }
            if (print) {
                DatariumMain.LOGGER.info("[CET] nbt property [single] print data: {}={}", path,
                        found == null ? "<NBT component not found>" : found.stream().map(CETNbt::asString).reduce("", (a, b) -> a + "\n" + b));
                DatariumMain.LOGGER.info("[CET] nbt property [single] print result: {}", inverts != pass);
            }
            return inverts != pass;
        }

        @Nullable
        private List<NBTBase> find(@Nullable NBTBase element, int index) {
            if (index >= steps.length || element == null) return null;
            String step = steps[index];
            boolean last = index == steps.length - 1;
            List<NBTBase> next = null;
            if (element instanceof NBTTagCompound compound) {
                NBTBase single = compound.hasKey(step) ? compound.getTag(step) : null;
                if (single != null) next = last ? Collections.singletonList(single) : find(single, index + 1);
            } else if (element instanceof NBTTagList list) {
                if ("*".equals(step)) {
                    if (!last) {
                        List<NBTBase> result = new ArrayList<>();
                        for (NBTBase tag : list) {
                            List<NBTBase> sub = find(tag, index + 1);
                            if (sub != null) result.addAll(sub);
                        }
                        next = result;
                    } else {
                        next = new ArrayList<>();
                        for (NBTBase tag : list) next.add(tag);
                    }
                } else if (!last && isInt(step)) {
                    int i = Integer.parseInt(step);
                    next = i >= 0 && i < list.tagCount() ? Collections.singletonList(list.get(i)) : null;
                }
            }
            return next == null || next.isEmpty() ? null : next;
        }
    }

    static String asString(NBTBase tag) {
        return tag instanceof NBTTagString string ? string.getString() : tag.toString();
    }

    @Nullable
    public static Predicate<String> stringMatcher(@Nullable String line) {
        if (line == null || line.isBlank()) return null;
        String match = line.trim();
        boolean invert = match.startsWith("!");
        if (invert) match = match.substring(1);

        if (match.startsWith("regex:") || match.startsWith("iregex:")) {
            boolean ignoreCase = match.startsWith("i");
            String body = match.replaceFirst("iregex:|regex:", "");
            Pattern regex = Pattern.compile(ignoreCase ? "(?i)" + body : body);
            return s -> invert != regex.matcher(s).matches();
        }
        if (match.startsWith("pattern:") || match.startsWith("ipattern:")) {
            boolean ignoreCase = match.startsWith("i");
            String body = "\\Q" + match.replaceFirst("ipattern:|pattern:", "").replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "\\E";
            Pattern regex = Pattern.compile(ignoreCase ? "(?i)" + body : body);
            return s -> invert != regex.matcher(s).matches();
        }
        String whole = match;
        List<String> split = Arrays.asList(whole.split("\\s+"));
        boolean hasQuotes = whole.contains("\"");
        return s -> {
            boolean found = s.equals(whole) || split.contains(s);
            if (!found && hasQuotes) {
                Matcher m = QUOTED_GROUPS.matcher(whole);
                while (m.find()) {
                    if (s.equals(m.group(1).replace("\"", "").trim())) {
                        found = true;
                        break;
                    }
                }
            }
            return invert != found;
        };
    }

    private static int[] intRange(String raw) {
        String digits = raw.trim().replaceAll("[^0-9-]", "");
        try {
            int a;
            int b;
            if (INT_RANGE.matcher(digits).matches()) {
                String[] parts = digits.split("(?<!^|-)-");
                a = Integer.parseInt(parts[0]);
                b = Integer.parseInt(parts[1]);
            } else {
                a = b = Integer.parseInt(digits);
            }
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (Exception e) {
            DatariumMain.LOGGER.error("[CET] Error parsing range: {}", raw);
            return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
        }
    }

    private static boolean isInt(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
