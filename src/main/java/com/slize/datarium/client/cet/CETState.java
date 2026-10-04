package com.slize.datarium.client.cet;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class CETState {
    private static final CETSubject NONE = CETSubject.virtual(CETSubject.GENERIC_UUID, null, BlockPos.ORIGIN);

    private static final Deque<CETSubject> SUBJECTS = new ArrayDeque<>();
    private static final Deque<Boolean> MODIFY = new ArrayDeque<>();
    private static final Map<Integer, CETTexture> BY_GL_ID = new HashMap<>();

    public static boolean isRenderingFeatures = false;
    public static OverlayPhase overlayPhase = OverlayPhase.NONE;
    @Nullable public static CETTexture currentTexture;
    @Nullable private static CETTexture pendingTexture;
    public static int modelPartDepth = 0;

    private CETState() {}

    public enum OverlayPhase {
        NONE, EMISSIVE, ENCHANT, GLINT
    }

    public static void mount(@Nullable CETSubject subject) {
        SUBJECTS.push(subject == null ? NONE : subject);
        MODIFY.push(true);
        currentTexture = null;
    }

    public static void unmount() {
        if (!SUBJECTS.isEmpty()) SUBJECTS.pop();
        if (!MODIFY.isEmpty()) MODIFY.pop();
        modelPartDepth = 0;
        currentTexture = null;
        if (SUBJECTS.isEmpty()) {
            isRenderingFeatures = false;
            overlayPhase = OverlayPhase.NONE;
        }
    }

    @Nullable
    public static CETSubject subject() {
        CETSubject subject = SUBJECTS.peek();
        return subject == NONE ? null : subject;
    }

    public static boolean isActive() {
        return subject() != null;
    }

    public static boolean isModifyAllowed() {
        Boolean allowed = MODIFY.peek();
        return allowed == null || allowed;
    }

    public static void pushModify(boolean allowed) {
        MODIFY.push(allowed);
    }

    public static void popModify() {
        if (!MODIFY.isEmpty()) MODIFY.pop();
    }

    public static void onTextureResolved(@Nullable CETTexture texture) {
        pendingTexture = texture;
        currentTexture = texture;
    }

    public static void onGlBind(int glId) {
        if (pendingTexture != null) {
            BY_GL_ID.put(glId, pendingTexture);
            pendingTexture = null;
        } else if (overlayPhase == OverlayPhase.NONE) {
            currentTexture = BY_GL_ID.get(glId);
        }
    }

    public static void verifyFrame() {
        if (SUBJECTS.isEmpty() && MODIFY.isEmpty() && overlayPhase == OverlayPhase.NONE) return;
        SUBJECTS.clear();
        MODIFY.clear();
        isRenderingFeatures = false;
        overlayPhase = OverlayPhase.NONE;
        currentTexture = null;
        pendingTexture = null;
        modelPartDepth = 0;
    }

    public static void clear() {
        SUBJECTS.clear();
        MODIFY.clear();
        BY_GL_ID.clear();
        isRenderingFeatures = false;
        overlayPhase = OverlayPhase.NONE;
        currentTexture = null;
        pendingTexture = null;
        modelPartDepth = 0;
    }
}
