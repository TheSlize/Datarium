package com.slize.datarium.mixin;

import com.slize.datarium.client.font.BitmapGlyph;
import com.slize.datarium.client.font.TrimResult;
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

    @Unique private final Map<Integer, BitmapGlyph> bitmapGlyphs = new HashMap<>();
    @Unique private final Map<Integer, Float> spaceAdvances = new HashMap<>();
    @Unique private boolean glyphsLoaded = false;
    @Unique private final Set<String> loadedReferences = new HashSet<>();
    @Unique private static final String FONT_JSON = "assets/datarium/font/default.json";
    @Unique private static final String ALPHABET = "0123456789abcdefklmnor";

    @Shadow public abstract int getCharWidth(char character);
    @Shadow protected abstract void setColor(float r, float g, float b, float a);
    @Shadow protected abstract float renderChar(char ch, boolean italic);

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    public void datarium$onResourceManagerReload(IResourceManager resourceManager, CallbackInfo ci) {
        this.glyphsLoaded = false;
        this.bitmapGlyphs.clear();
        this.spaceAdvances.clear();
        this.loadedReferences.clear();
    }

    @Unique
    private void datarium$loadCustomGlyphs() {
        if (!this.glyphsLoaded) {
            this.glyphsLoaded = true;
            this.loadedReferences.clear();

            // Load the mod's internal font as fallback
            datarium$loadFontFromClasspath();

            // Load minecraft:font/default.json from resource packs (can add/override glyphs)
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

        for (JsonElement el : root.getAsJsonArray("providers")) {
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
            }
        }
    }

    @Unique
    private void datarium$loadReferenceProvider(JsonObject prov) {
        if (!prov.has("id")) return;

        String id = prov.get("id").getAsString();

        // Prevent infinite loops
        if (loadedReferences.contains(id)) return;
        loadedReferences.add(id);

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
                spaceAdvances.put(codePoints[0], advance);
            }
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
                                BitmapGlyph glyph = new BitmapGlyph(atlasLoc, ascent, renderHeight, (float) advancePx, (float) drawWidthPx, u0, v0, u1, v1);
                                this.bitmapGlyphs.put(codePoint, glyph);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Texture not found, ignore silently
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
    private int datarium$getCodePointWidth(int codePoint) {
        // Check space advances first
        Float spaceAdvance = spaceAdvances.get(codePoint);
        if (spaceAdvance != null) {
            return spaceAdvance.intValue();
        }

        // Check bitmap glyphs
        BitmapGlyph glyph = bitmapGlyphs.get(codePoint);
        if (glyph != null) {
            return (int) glyph.advance();
        }

        return -1; // Not found in custom glyphs
    }

    @Unique
    private float datarium$renderCodePoint(int codePoint, boolean italic) {
        // Check space advances (just advance, no rendering)
        Float spaceAdvance = spaceAdvances.get(codePoint);
        if (spaceAdvance != null) {
            return spaceAdvance;
        }

        // Check bitmap glyphs
        BitmapGlyph glyph = bitmapGlyphs.get(codePoint);
        if (glyph != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(glyph.texture());
            float x = this.posX;
            float y = this.posY + (7.0F - (float) glyph.ascent());
            float italicOffset = italic ? 1.0F : 0.0F;
            float drawW = glyph.width();
            float h = (float) glyph.cellHeight();
            float u0 = glyph.u0();
            float v0 = glyph.v0();
            float u1 = glyph.u1();
            float v1 = glyph.v1();

            GlStateManager.glBegin(7); // GL_QUADS
            GlStateManager.glTexCoord2f(u0, v0);
            GlStateManager.glVertex3f(x + italicOffset, y, 0.0F);
            GlStateManager.glTexCoord2f(u0, v1);
            GlStateManager.glVertex3f(x - italicOffset, y + h, 0.0F);
            GlStateManager.glTexCoord2f(u1, v1);
            GlStateManager.glVertex3f(x - italicOffset + drawW, y + h, 0.0F);
            GlStateManager.glTexCoord2f(u1, v0);
            GlStateManager.glVertex3f(x + italicOffset + drawW, y, 0.0F);
            GlStateManager.glEnd();

            return glyph.advance();
        }

        return -1; // Not found
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    public void datarium$getCharWidth(char character, CallbackInfoReturnable<Integer> cir) {
        this.datarium$loadCustomGlyphs();
        int width = datarium$getCodePointWidth(character);
        if (width >= 0) {
            cir.setReturnValue(width);
        }
    }

    @Inject(method = "renderChar", at = @At("HEAD"), cancellable = true)
    private void datarium$renderChar(char ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        this.datarium$loadCustomGlyphs();
        float result = datarium$renderCodePoint(ch, italic);
        if (result >= 0) {
            cir.setReturnValue(result);
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

            int width = datarium$getCodePointWidth(codePoint);
            if (width >= 0) {
                float w = width;
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

    @Overwrite
    public int sizeStringToWidth(String str, int wrapWidth) {
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

            int width = datarium$getCodePointWidth(codePoint);
            float w;
            if (width >= 0) {
                w = width;
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
                int w = datarium$getCodePointWidth(codePoint);
                float charWidth;
                if (w >= 0) {
                    charWidth = w;
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
    public void renderStringAtPos(String text, boolean shadow) {
        this.datarium$loadCustomGlyphs();

        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == 167 && i + charCount < text.length()) { // §
                int nextCodePoint = text.codePointAt(i + charCount);
                char fmtChar = Character.toLowerCase((char) nextCodePoint);
                int i1 = ALPHABET.indexOf(fmtChar);

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

            // Check if we have a custom glyph for this code point
            boolean hasCustomGlyph = bitmapGlyphs.containsKey(codePoint) || spaceAdvances.containsKey(codePoint);

            // For randomStyle, only apply to vanilla characters
            int renderCodePoint = codePoint;
            if (this.randomStyle && !hasCustomGlyph && codePoint <= 0xFFFF) {
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

            boolean treatAsUnicode = !hasCustomGlyph && (this.unicodeFlag || (renderCodePoint <= 0xFFFF && "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000".indexOf((char) renderCodePoint) == -1));
            float f1 = treatAsUnicode ? 0.5F : 1.0F;
            boolean flag = (renderCodePoint == 0 || treatAsUnicode) && shadow;

            if (flag) {
                this.posX -= f1;
                this.posY -= f1;
            }

            float f;
            if (hasCustomGlyph) {
                f = datarium$renderCodePoint(renderCodePoint, this.italicStyle);
            } else if (renderCodePoint <= 0xFFFF) {
                f = this.renderChar((char) renderCodePoint, this.italicStyle);
            } else {
                f = 0;
            }

            if (flag) {
                this.posX += f1;
                this.posY += f1;
            }

            if (this.boldStyle) {
                this.posX += f1;
                if (flag) {
                    this.posX -= f1;
                    this.posY -= f1;
                }

                if (hasCustomGlyph) {
                    datarium$renderCodePoint(renderCodePoint, this.italicStyle);
                } else if (renderCodePoint <= 0xFFFF) {
                    this.renderChar((char) renderCodePoint, this.italicStyle);
                }

                this.posX -= f1;
                if (flag) {
                    this.posX += f1;
                    this.posY += f1;
                }
                f++;
            }

            this.posX += f;
            i += charCount;
        }
    }
}