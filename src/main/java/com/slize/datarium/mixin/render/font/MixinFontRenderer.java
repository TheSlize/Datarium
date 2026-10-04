package com.slize.datarium.mixin.render.font;

import com.slize.datarium.client.font.BitmapGlyph;
import com.slize.datarium.client.font.GlyphAtlas;
import com.slize.datarium.client.font.GlyphSource;
import com.slize.datarium.client.font.LegacyUnicodeGlyphs;
import com.slize.datarium.client.font.TrueTypeGlyphs;
import com.slize.datarium.client.font.TrimResult;
import com.slize.datarium.client.font.UnihexGlyphs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {
    @Shadow protected float posX;
    @Shadow protected float posY;
    @Shadow private int textColor;
    @Shadow private float alpha;
    @Shadow private boolean boldStyle;
    @Shadow private boolean italicStyle;
    @Shadow private boolean randomStyle;
    @Shadow private boolean strikethroughStyle;
    @Shadow private boolean underlineStyle;
    @Shadow private float red;
    @Shadow private float green;
    @Shadow private float blue;
    @Final @Shadow private int[] colorCode;
    @Shadow public Random fontRandom;
    @Shadow private boolean unicodeFlag;
    @Final @Shadow public ResourceLocation locationFontTexture;

    @Unique private final Map<Integer, BitmapGlyph> datarium$bitmapGlyphs = new HashMap<>();
    @Unique private final List<GlyphSource> datarium$sources = new ArrayList<>();
    @Unique private final GlyphAtlas datarium$atlas = new GlyphAtlas();
    @Unique private boolean datarium$main = true;
    @Unique private boolean datarium$glyphsLoaded = false;
    @Unique private final Set<String> datarium$loadedReferences = new HashSet<>();
    @Unique private static final String FONT_JSON = "assets/datarium/font/default.json";
    @Unique private static final String datarium$ALPHABET = "0123456789abcdefklmnor";

    @Shadow public abstract int getCharWidth(char character);
    @Shadow protected abstract void setColor(float r, float g, float b, float a);
    @Shadow protected abstract float renderChar(char ch, boolean italic);

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    public void datarium$onResourceManagerReload(IResourceManager resourceManager, CallbackInfo ci) {
        this.datarium$glyphsLoaded = false;
        this.datarium$bitmapGlyphs.clear();
        this.datarium$sources.clear();
        this.datarium$atlas.clear();
        this.datarium$loadedReferences.clear();
    }

    @Unique
    private void datarium$loadCustomGlyphs() {
        if (!this.datarium$glyphsLoaded) {
            this.datarium$glyphsLoaded = true;
            this.datarium$loadedReferences.clear();
            this.datarium$main = "textures/font/ascii.png".equals(this.locationFontTexture.getPath());

            // Load the mod's internal font as fallback
            datarium$loadFontFromClasspath();

            // Load minecraft:font/default.json from resource packs; note that we can add/override glyphs deriving from there
            datarium$loadFontFromResource(new ResourceLocation("minecraft", "font/default.json"));
        }
    }

    @Unique
    private void datarium$loadFontFromClasspath() {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(MixinFontRenderer.FONT_JSON)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                datarium$parseProviders(root);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Unique
    private void datarium$loadFontFromResource(ResourceLocation loc) {
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(loc);
            try (InputStream in = resource.getInputStream()) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                datarium$parseProviders(root);
            }
        } catch (Exception e) {
            // Resource not found, ignore silently
        }
    }

    @Unique
    private void datarium$parseProviders(JsonObject root) {
        if (!root.has("providers")) return;

        JsonArray providers = root.getAsJsonArray("providers");
        for (int i = providers.size() - 1; i >= 0; --i) {
            JsonElement el = providers.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject prov = el.getAsJsonObject();
            String type = prov.has("type") ? prov.get("type").getAsString() : "";

            switch (type) {
                case "bitmap":
                    datarium$loadBitmapProvider(prov);
                    break;
                case "reference":
                    datarium$loadReferenceProvider(prov);
                    break;
                case "space":
                    datarium$loadSpaceProvider(prov);
                    break;
                case "ttf":
                    datarium$loadTtfProvider(prov);
                    break;
                case "unihex":
                    datarium$loadUnihexProvider(prov);
                    break;
                case "legacy_unicode":
                    datarium$loadLegacyUnicodeProvider(prov);
                    break;
            }
        }
    }

    @Unique
    private void datarium$loadReferenceProvider(JsonObject prov) {
        if (!prov.has("id")) return;

        String id = prov.get("id").getAsString();

        // Prevent infinite loops
        if (datarium$loadedReferences.contains(id)) return;
        datarium$loadedReferences.add(id);

        // Parse the id as a ResourceLocation
        ResourceLocation refLoc;
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            refLoc = new ResourceLocation(parts[0], "font/" + parts[1] + ".json");
        } else {
            refLoc = new ResourceLocation("minecraft", "font/" + id + ".json");
        }

        datarium$loadFontFromResource(refLoc);
    }

    @Unique
    private void datarium$loadSpaceProvider(JsonObject prov) {
        if (!prov.has("advances")) return;

        JsonObject advances = prov.getAsJsonObject("advances");
        for (String key : advances.keySet()) {
            float advance = advances.get(key).getAsFloat();
            // Parse the key - it may contain surrogate pairs
            int[] codePoints = key.codePoints().toArray();
            if (codePoints.length > 0) {
                datarium$bitmapGlyphs.put(codePoints[0], BitmapGlyph.space(advance));
            }
        }
    }

    @Unique
    private void datarium$addSource(GlyphSource source) {
        this.datarium$bitmapGlyphs.keySet().removeIf(source::has);
        this.datarium$sources.add(0, source);
    }

    @Unique
    private void datarium$loadTtfProvider(JsonObject prov) {
        if (!prov.has("file")) return;

        try {
            ResourceLocation id = new ResourceLocation(prov.get("file").getAsString());
            float size = prov.has("size") ? prov.get("size").getAsFloat() : 11.0F;
            float oversample = prov.has("oversample") ? prov.get("oversample").getAsFloat() : 1.0F;
            float shiftX = 0.0F;
            float shiftY = 0.0F;
            if (prov.has("shift")) {
                JsonArray shift = prov.getAsJsonArray("shift");
                shiftX = shift.get(0).getAsFloat();
                shiftY = shift.get(1).getAsFloat();
            }

            Set<Integer> skip = new HashSet<>();
            if (prov.has("skip")) {
                JsonElement skipEl = prov.get("skip");
                if (skipEl.isJsonArray()) {
                    for (JsonElement el : skipEl.getAsJsonArray()) {
                        el.getAsString().codePoints().forEach(skip::add);
                    }
                } else {
                    skipEl.getAsString().codePoints().forEach(skip::add);
                }
            }

            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(id.getNamespace(), "font/" + id.getPath()));
            try (InputStream in = resource.getInputStream()) {
                datarium$addSource(new TrueTypeGlyphs(in, size, oversample, shiftX, shiftY, skip));
            }
        } catch (Exception e) {
        }
    }

    @Unique
    private void datarium$loadUnihexProvider(JsonObject prov) {
        if (!prov.has("hex_file")) return;

        try {
            List<int[]> overrides = new ArrayList<>();
            if (prov.has("size_overrides")) {
                for (JsonElement el : prov.getAsJsonArray("size_overrides")) {
                    JsonObject range = el.getAsJsonObject();
                    overrides.add(new int[]{range.get("from").getAsString().codePointAt(0), range.get("to").getAsString().codePointAt(0), range.get("left").getAsInt(), range.get("right").getAsInt()});
                }
            }

            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(prov.get("hex_file").getAsString()));
            try (InputStream in = resource.getInputStream()) {
                datarium$addSource(new UnihexGlyphs(in, overrides));
            }
        } catch (Exception e) {
        }
    }

    @Unique
    private void datarium$loadLegacyUnicodeProvider(JsonObject prov) {
        if (!prov.has("sizes") || !prov.has("template")) return;

        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(prov.get("sizes").getAsString()));
            try (InputStream in = resource.getInputStream()) {
                datarium$addSource(new LegacyUnicodeGlyphs(in.readAllBytes(), prov.get("template").getAsString()));
            }
        } catch (Exception e) {
        }
    }

    @Unique
    private void datarium$loadBitmapProvider(JsonObject prov) {
        if (!prov.has("file") || !prov.has("ascent") || !prov.has("chars")) return;

        String file = prov.get("file").getAsString();
        int ascent = prov.get("ascent").getAsInt();
        Integer cellHeightFromJson = prov.has("height") ? prov.get("height").getAsInt() : null;
        JsonArray charsLines = prov.getAsJsonArray("chars");
        List<String> lines = new ArrayList<>();

        for (JsonElement lineEl : charsLines) {
            lines.add(lineEl.getAsString());
        }

        // Build the texture ResourceLocation
        ResourceLocation atlasLoc = datarium$resolveTexturePath(file);

        // Force the TextureManager to delete the old texture so it reloads from the new resource pack
        Minecraft.getMinecraft().getTextureManager().deleteTexture(atlasLoc);

        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(atlasLoc);
            try (InputStream texIn = resource.getInputStream()) {
                BufferedImage img = ImageIO.read(texIn);
                if (img == null) {
                    return;
                }

                int imgW = img.getWidth();
                int imgH = img.getHeight();
                int rows = Math.max(1, lines.size());

                // Calculate max columns based on code point count
                int maxCols = 0;
                for (String line : lines) {
                    int cols = (int) line.codePoints().count();
                    if (cols > maxCols) maxCols = cols;
                }
                if (maxCols == 0) maxCols = 16;

                int cellW = imgW / maxCols;
                int cellH = imgH / rows;

                if (cellW <= 0 || cellH <= 0) {
                    return;
                }

                for (int row = 0; row < rows; ++row) {
                    String line = lines.get(row);
                    int[] codePoints = line.codePoints().toArray();
                    int y0 = row * cellH;
                    if (y0 + cellH > imgH) break;

                    for (int col = 0; col < codePoints.length; ++col) {
                        int codePoint = codePoints[col];
                        if (codePoint != 0) {
                            int x0 = col * cellW;
                            if (x0 + cellW > imgW) break;

                            TrimResult trim = datarium$scanTrimX(img, x0, y0, cellW, cellH);
                            if (!trim.empty()) {
                                int drawWidthPx = Math.max(0, trim.widthPx());
                                int advancePx = drawWidthPx + 1;
                                float u0 = (float) (x0 + trim.leftPx()) / (float) imgW;
                                float u1 = (float) (x0 + trim.leftPx() + trim.widthPx()) / (float) imgW;
                                float v0 = (float) y0 / (float) imgH;
                                float v1 = (float) (y0 + cellH) / (float) imgH;

                                int renderHeight = cellHeightFromJson != null ? cellHeightFromJson : cellH;
                                if (codePoint >= 0x20 && codePoint <= 0x7E) {
                                    this.datarium$bitmapGlyphs.put(codePoint, BitmapGlyph.VANILLA);
                                    continue;
                                }
                                BitmapGlyph glyph = new BitmapGlyph(atlasLoc, 0.0F, (float) ascent, (float) renderHeight, (float) advancePx, (float) drawWidthPx, 1.0F, false, u0, v0, u1, v1);
                                this.datarium$bitmapGlyphs.put(codePoint, glyph);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    @Unique
    private ResourceLocation datarium$resolveTexturePath(String file) {
        String namespace;
        String path;

        if (file.contains(":")) {
            String[] parts = file.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        } else {
            namespace = "minecraft";
            path = file;
        }

        // Add textures/ prefix if not already present
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }

        return new ResourceLocation(namespace, path);
    }

    @Unique
    private static TrimResult datarium$scanTrimX(BufferedImage img, int x0, int y0, int w, int h) {
        int left = -1;
        int right = -1;

        for (int x = 0; x < w; ++x) {
            if (datarium$colHasAlpha(img, x0 + x, y0, h)) {
                left = x;
                break;
            }
        }

        if (left == -1) {
            return new TrimResult(true, 0, 0);
        }

        for (int x = w - 1; x >= 0; --x) {
            if (datarium$colHasAlpha(img, x0 + x, y0, h)) {
                right = x;
                break;
            }
        }

        if (right < left) {
            return new TrimResult(true, 0, 0);
        }

        int width = right - left + 1;
        return new TrimResult(false, left, width);
    }

    @Unique
    private static boolean datarium$colHasAlpha(BufferedImage img, int x, int y0, int h) {
        for (int y = 0; y < h; ++y) {
            int argb = img.getRGB(x, y0 + y);
            int a = (argb >>> 24) & 255;
            if (a > 0) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private BitmapGlyph datarium$glyph(int codePoint) {
        if (!this.datarium$main && codePoint >= 0x20 && codePoint <= 0x7E) return null;

        BitmapGlyph glyph = this.datarium$bitmapGlyphs.get(codePoint);
        if (glyph == null) {
            for (GlyphSource source : this.datarium$sources) {
                if (source.has(codePoint)) {
                    glyph = source.bake(codePoint, this.datarium$atlas);
                    this.datarium$bitmapGlyphs.put(codePoint, glyph);
                    break;
                }
            }
        }

        return glyph == BitmapGlyph.VANILLA ? null : glyph;
    }

    @Unique
    private float datarium$renderGlyph(BitmapGlyph glyph, boolean italic) {
        if (glyph.texture() == null) {
            return glyph.advance();
        }

        this.datarium$atlas.flush();
        Minecraft.getMinecraft().getTextureManager().bindTexture(glyph.texture());
        float x = this.posX + glyph.bearing();
        float top = 7.0F - glyph.ascent();
        float bottom = top + glyph.cellHeight();
        float y = this.posY + top;
        float topOffset = italic ? 1.0F - 0.25F * top : 0.0F;
        float bottomOffset = italic ? 1.0F - 0.25F * bottom : 0.0F;
        float drawW = glyph.width();
        float h = glyph.cellHeight();
        float u0 = glyph.u0();
        float v0 = glyph.v0();
        float u1 = glyph.u1();
        float v1 = glyph.v1();

        GlStateManager.glBegin(7); // GL_QUADS
        GlStateManager.glTexCoord2f(u0, v0);
        GlStateManager.glVertex3f(x + topOffset, y, 0.0F);
        GlStateManager.glTexCoord2f(u0, v1);
        GlStateManager.glVertex3f(x + bottomOffset, y + h, 0.0F);
        GlStateManager.glTexCoord2f(u1, v1);
        GlStateManager.glVertex3f(x + bottomOffset + drawW, y + h, 0.0F);
        GlStateManager.glTexCoord2f(u1, v0);
        GlStateManager.glVertex3f(x + topOffset + drawW, y, 0.0F);
        GlStateManager.glEnd();

        return glyph.advance();
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    public void datarium$getCharWidth(char character, CallbackInfoReturnable<Integer> cir) {
        this.datarium$loadCustomGlyphs();
        BitmapGlyph glyph = datarium$glyph(character);
        if (glyph != null) {
            cir.setReturnValue(MathHelper.ceil(glyph.advance()));
        }
    }

    @Inject(method = "renderChar", at = @At("HEAD"), cancellable = true)
    private void datarium$renderChar(char ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        this.datarium$loadCustomGlyphs();
        BitmapGlyph glyph = datarium$glyph(ch);
        if (glyph != null) {
            cir.setReturnValue(datarium$renderGlyph(glyph, italic));
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void datarium$getStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null) {
            cir.setReturnValue(0);
            return;
        }
        this.datarium$loadCustomGlyphs();
        float totalWidth = 0.0F;
        boolean bold = false;

        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == 167 && i + 1 < text.length()) { // §
                i++;
                char fmt = Character.toLowerCase(text.charAt(i));
                if (fmt == 'l') {
                    bold = true;
                } else if (fmt == 'r' || "0123456789abcdef".indexOf(fmt) >= 0) {
                    bold = false;
                }
                i++;
                continue;
            }

            BitmapGlyph glyph = datarium$glyph(codePoint);
            if (glyph != null) {
                float w = glyph.advance();
                if (bold && w > 0) {
                    w++;
                }
                totalWidth += w;
            } else if (codePoint <= 0xFFFF) {
                // Fall back to vanilla for BMP characters not in our map
                int k = this.getCharWidth((char) codePoint);
                if (k < 0 && i + charCount < text.length()) {
                    i += charCount;
                    char fmtChar = Character.toLowerCase(text.charAt(i));
                    if (fmtChar == 'l') {
                        bold = true;
                    } else if (fmtChar == 'r' || "0123456789abcdef".indexOf(fmtChar) >= 0) {
                        bold = false;
                    }
                    i++;
                    continue;
                }
                totalWidth += k;
                if (bold && k > 0) {
                    totalWidth++;
                }
            }

            i += charCount;
        }

        cir.setReturnValue(Math.round(totalWidth));
    }

    @Deprecated
    @Overwrite
    private int sizeStringToWidth(String str, int wrapWidth) {
        this.datarium$loadCustomGlyphs();
        int len = str.length();
        float widthSoFar = 0.0F;
        int lastSpace = -1;
        boolean bold = false;

        int i = 0;
        while (i < len) {
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '\n') {
                return i + charCount;
            }

            if (codePoint == ' ') {
                lastSpace = i;
            }

            if (codePoint == 167 && i + charCount < len) { // §
                i += charCount;
                char fmt = Character.toLowerCase(str.charAt(i));
                if (fmt == 'l') {
                    bold = true;
                } else if (fmt == 'r' || "0123456789abcdef".indexOf(fmt) >= 0) {
                    bold = false;
                }
                i++;
                continue;
            }

            BitmapGlyph glyph = datarium$glyph(codePoint);
            float w;
            if (glyph != null) {
                w = glyph.advance();
            } else if (codePoint <= 0xFFFF) {
                w = this.getCharWidth((char) codePoint);
            } else {
                w = 0;
            }

            widthSoFar += w;
            if (bold && w > 0) {
                widthSoFar++;
            }

            if (widthSoFar > (float) wrapWidth) {
                break;
            }

            i += charCount;
        }

        return i != len && lastSpace != -1 && lastSpace < i ? lastSpace : i;
    }

    @Overwrite
    public String trimStringToWidth(String text, int width, boolean reverse) {
        this.datarium$loadCustomGlyphs();
        StringBuilder stringbuilder = new StringBuilder();
        float totalWidth = 0.0F;
        boolean bold = false;
        boolean nextIsFormat = false;

        int[] codePoints = text.codePoints().toArray();
        int start = reverse ? codePoints.length - 1 : 0;
        int end = reverse ? -1 : codePoints.length;
        int step = reverse ? -1 : 1;

        for (int idx = start; idx != end && totalWidth < (float) width; idx += step) {
            int codePoint = codePoints[idx];

            if (nextIsFormat) {
                nextIsFormat = false;
                char fmt = Character.toLowerCase((char) codePoint);
                if (fmt == 'l') {
                    bold = true;
                } else if (fmt == 'r' || "0123456789abcdef".indexOf(fmt) >= 0) {
                    bold = false;
                }
            } else if (codePoint == 167) { // §
                nextIsFormat = true;
            } else {
                BitmapGlyph glyph = datarium$glyph(codePoint);
                float charWidth;
                if (glyph != null) {
                    charWidth = glyph.advance();
                } else if (codePoint <= 0xFFFF) {
                    charWidth = this.getCharWidth((char) codePoint);
                } else {
                    charWidth = 0;
                }

                totalWidth += charWidth;
                if (bold && charWidth > 0) {
                    totalWidth++;
                }
            }

            if (totalWidth > (float) width) {
                break;
            }

            String chars = new String(Character.toChars(codePoint));
            if (reverse) {
                stringbuilder.insert(0, chars);
            } else {
                stringbuilder.append(chars);
            }
        }

        return stringbuilder.toString();
    }

    @Overwrite
    private void renderStringAtPos(String text, boolean shadow) {
        this.datarium$loadCustomGlyphs();
        int i = 0;
        boolean blended = false;

        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == 167 && i + charCount < text.length()) { // §
                int nextCodePoint = text.codePointAt(i + charCount);
                char fmtChar = Character.toLowerCase((char) nextCodePoint);
                int i1 = datarium$ALPHABET.indexOf(fmtChar);

                if (i1 < 16) {
                    this.randomStyle = false;
                    this.boldStyle = false;
                    this.strikethroughStyle = false;
                    this.underlineStyle = false;
                    this.italicStyle = false;
                    if (i1 < 0) {
                        i1 = 15;
                    }
                    if (shadow) {
                        i1 += 16;
                    }
                    int j1 = this.colorCode[i1];
                    this.textColor = j1;
                    this.setColor((float) (j1 >> 16) / 255.0F, (float) (j1 >> 8 & 255) / 255.0F, (float) (j1 & 255) / 255.0F, this.alpha);
                } else if (i1 == 16) {
                    this.randomStyle = true;
                } else if (i1 == 17) {
                    this.boldStyle = true;
                } else if (i1 == 18) {
                    this.strikethroughStyle = true;
                } else if (i1 == 19) {
                    this.underlineStyle = true;
                } else if (i1 == 20) {
                    this.italicStyle = true;
                } else {
                    this.randomStyle = false;
                    this.boldStyle = false;
                    this.strikethroughStyle = false;
                    this.underlineStyle = false;
                    this.italicStyle = false;
                    this.setColor(this.red, this.green, this.blue, this.alpha);
                }

                i += charCount + Character.charCount(nextCodePoint);
                continue;
            }


            int renderCodePoint = codePoint;
            if (this.randomStyle && codePoint <= 0xFFFF) {
                int j = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000".indexOf((char) codePoint);
                if (j != -1) {
                    int k = this.getCharWidth((char) codePoint);
                    char c1;
                    do {
                        j = this.fontRandom.nextInt("ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000".length());
                        c1 = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000".charAt(j);
                    } while (k != this.getCharWidth(c1));
                    renderCodePoint = c1;
                }
            }

            BitmapGlyph glyph = datarium$glyph(renderCodePoint);
            boolean hasCustomGlyph = glyph != null;
            boolean treatAsUnicode = !hasCustomGlyph && (this.unicodeFlag || (renderCodePoint <= 0xFFFF && "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000".indexOf((char) renderCodePoint) == -1));
            float f1 = hasCustomGlyph ? glyph.offset() : treatAsUnicode ? 0.5F : 1.0F;
            float f2 = hasCustomGlyph ? 1.0F - f1 : f1;
            boolean flag = (hasCustomGlyph ? f2 != 0.0F : renderCodePoint == 0 || treatAsUnicode) && shadow;

            if (hasCustomGlyph && glyph.smooth() && !blended) {
                blended = true;
                GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            }

            if (flag) {
                this.posX -= f2;
                this.posY -= f2;
            }

            float f;
            if (hasCustomGlyph) {
                f = datarium$renderGlyph(glyph, this.italicStyle);
            } else if (renderCodePoint <= 0xFFFF) {
                f = this.renderChar((char) renderCodePoint, this.italicStyle);
            } else {
                f = 0;
            }

            if (flag) {
                this.posX += f2;
                this.posY += f2;
            }

            if (this.boldStyle) {
                this.posX += f1;
                if (flag) {
                    this.posX -= f2;
                    this.posY -= f2;
                }

                if (hasCustomGlyph) {
                    datarium$renderGlyph(glyph, this.italicStyle);
                } else if (renderCodePoint <= 0xFFFF) {
                    this.renderChar((char) renderCodePoint, this.italicStyle);
                }

                this.posX -= f1;
                if (flag) {
                    this.posX += f2;
                    this.posY += f2;
                }
                f++;
            }

            this.posX += f;
            i += charCount;
        }

        if (blended) {
            GL11.glPopAttrib();
        }
    }
}