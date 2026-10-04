package com.slize.datarium.client.cet;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;

final class CETLayoutConverter {
    private static final int WEST = 0;
    private static final int NORTH = 1;
    private static final int EAST = 2;
    private static final int SOUTH = 3;
    private static final int DOWN = 4;
    private static final int UP = 5;

    private static final int FLIP_U = 1;
    private static final int FLIP_V = 2;
    private static final int ROTATE = FLIP_U | FLIP_V;
    private static final int TRANSPOSE = 4;

    private static final int[][] CHEST = {{0, 0, 14, 5, 14}, {0, 19, 14, 10, 14}, {0, 0, 2, 4, 1}};
    private static final int[][] CHEST_HALF = {{0, 0, 15, 5, 14}, {0, 19, 15, 10, 14}, {0, 0, 1, 4, 1}};
    private static final int[][] CHEST_DOUBLE = {{0, 0, 30, 5, 14}, {0, 19, 30, 10, 14}, {0, 0, 2, 4, 1}};

    private static final int[] M_BODY = {0, 32, 10, 10, 22};
    private static final int[] M_NECK = {0, 35, 4, 12, 7};
    private static final int[] M_HEAD = {0, 13, 6, 5, 7};
    private static final int[] M_MOUTH = {0, 25, 4, 5, 5};
    private static final int[] M_EAR = {19, 16, 2, 3, 1};
    private static final int[] M_MULE_EAR = {0, 12, 2, 7, 1};
    private static final int[] M_MANE = {56, 36, 2, 16, 2};
    private static final int[] M_TAIL = {42, 36, 3, 14, 4};
    private static final int[] M_LEG = {48, 21, 4, 11, 4};
    private static final int[] M_CHEST = {26, 21, 8, 8, 3};

    private static final int[] L_BODY = {0, 34, 10, 10, 24};
    private static final int[] L_NECK = {0, 12, 4, 14, 8};
    private static final int[] L_HEAD = {0, 0, 5, 5, 7};
    private static final int[] L_UPPER_MOUTH = {24, 18, 4, 3, 6};
    private static final int[] L_LOWER_MOUTH = {24, 27, 4, 2, 5};
    private static final int[] L_EAR = {0, 0, 2, 3, 1};
    private static final int[] L_MULE_EAR = {0, 12, 2, 7, 1};
    private static final int[] L_MANE = {58, 0, 2, 16, 4};
    private static final int[][] L_TAIL = {{44, 0, 2, 2, 3}, {38, 7, 3, 4, 7}, {24, 3, 3, 4, 7}};
    private static final double[] TAIL_CUTS = {0, 3 / 16.0, 3 / 16.0, 10 / 16.0, 9 / 16.0, 1};
    private static final int[][] L_BACK_LEFT_LEG = {{78, 29, 4, 9, 5}, {78, 43, 3, 5, 3}, {78, 51, 4, 3, 4}};
    private static final int[][] L_BACK_RIGHT_LEG = {{96, 29, 4, 9, 5}, {96, 43, 3, 5, 3}, {96, 51, 4, 3, 4}};
    private static final int[][] L_FRONT_LEFT_LEG = {{44, 29, 3, 8, 4}, {44, 41, 3, 5, 3}, {44, 51, 4, 3, 4}};
    private static final int[][] L_FRONT_RIGHT_LEG = {{60, 29, 3, 8, 4}, {60, 41, 3, 5, 3}, {60, 51, 4, 3, 4}};
    private static final double[] LEG_CUTS = {0, 3 / 11.0, 8 / 11.0, 1};
    private static final int[] L_CHEST_LEFT = {0, 34, 8, 8, 3};
    private static final int[] L_CHEST_RIGHT = {0, 47, 8, 8, 3};

    private CETLayoutConverter() {}

    private record Sheet(BufferedImage image, double scale) {
        static Sheet of(BufferedImage image, int width) {
            return new Sheet(image, image.getWidth() / (double) width);
        }

        static Sheet blank(int width, int height, int scale) {
            return new Sheet(new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB), scale);
        }
    }

    static BufferedImage chest(BufferedImage modern) {
        Sheet src = Sheet.of(modern, 64);
        Sheet dst = Sheet.blank(64, 64, Math.max(1, modern.getWidth() / 64));
        double[] all = {0, 0, 64, 64};
        copy(src, all, dst, all, 0);
        for (int[] box : CHEST) chestBox(src, box, dst, box, 0, 1, true, true);
        return dst.image;
    }

    static BufferedImage doubleChest(BufferedImage left, BufferedImage right) {
        Sheet leftSrc = Sheet.of(left, 64);
        Sheet rightSrc = Sheet.of(right, 64);
        Sheet dst = Sheet.blank(128, 64, Math.max(1, left.getWidth() / 64));
        for (int i = 0; i < CHEST_HALF.length; i++) {
            chestBox(rightSrc, CHEST_HALF[i], dst, CHEST_DOUBLE[i], 0, 0.5, true, false);
            chestBox(leftSrc, CHEST_HALF[i], dst, CHEST_DOUBLE[i], 0.5, 1, false, true);
        }
        return dst.image;
    }

    private static void chestBox(Sheet src, int[] from, Sheet dst, int[] to, double start, double end, boolean west, boolean east) {
        copy(src, face(from, UP), dst, columns(face(to, DOWN), start, end), FLIP_V);
        copy(src, face(from, DOWN), dst, columns(face(to, UP), start, end), FLIP_V);
        copy(src, face(from, SOUTH), dst, columns(face(to, NORTH), start, end), ROTATE);
        copy(src, face(from, NORTH), dst, columns(face(to, SOUTH), 1 - end, 1 - start), ROTATE);
        if (west) copy(src, face(from, WEST), dst, face(to, WEST), ROTATE);
        if (east) copy(src, face(from, EAST), dst, face(to, EAST), ROTATE);
    }

    static BufferedImage horse(BufferedImage modern, @Nullable BufferedImage canvas) {
        Sheet src = Sheet.of(modern, 64);
        int scale = Math.max(1, (int) Math.ceil(src.scale));
        if (canvas != null) scale = Math.max(scale, canvas.getWidth() / 128);
        Sheet dst = Sheet.blank(128, 128, scale);
        if (canvas != null) {
            double[] all = {0, 0, 128, 128};
            copy(Sheet.of(canvas, 128), all, dst, all, 0);
        }

        box(src, M_BODY, dst, L_BODY);
        box(src, M_NECK, dst, L_NECK);
        box(src, M_HEAD, dst, L_HEAD);
        box(src, M_EAR, dst, L_EAR);
        box(src, M_MULE_EAR, dst, L_MULE_EAR);
        box(src, M_MANE, dst, L_MANE);

        box(src, M_CHEST, dst, L_CHEST_LEFT);
        copy(src, face(M_CHEST, SOUTH), dst, face(L_CHEST_RIGHT, NORTH), 0);
        copy(src, face(M_CHEST, NORTH), dst, face(L_CHEST_RIGHT, SOUTH), 0);
        copy(src, face(M_CHEST, EAST), dst, face(L_CHEST_RIGHT, WEST), 0);
        copy(src, face(M_CHEST, WEST), dst, face(L_CHEST_RIGHT, EAST), 0);
        copy(src, face(M_CHEST, DOWN), dst, face(L_CHEST_RIGHT, DOWN), ROTATE);
        copy(src, face(M_CHEST, UP), dst, face(L_CHEST_RIGHT, UP), ROTATE);

        double lip = 3 / 5.0;
        for (int side = WEST; side <= SOUTH; side++) {
            copy(src, rows(face(M_MOUTH, side), 0, lip), dst, face(L_UPPER_MOUTH, side), 0);
            copy(src, rows(face(M_MOUTH, side), lip, 1), dst, face(L_LOWER_MOUTH, side), 0);
        }
        copy(src, face(M_MOUTH, DOWN), dst, face(L_UPPER_MOUTH, DOWN), 0);
        copy(src, face(M_MOUTH, UP), dst, face(L_LOWER_MOUTH, UP), 0);

        for (int i = 0; i < L_TAIL.length; i++) {
            double start = TAIL_CUTS[i * 2];
            double end = TAIL_CUTS[i * 2 + 1];
            copy(src, rows(face(M_TAIL, SOUTH), start, end), dst, face(L_TAIL[i], DOWN), ROTATE);
            copy(src, rows(face(M_TAIL, NORTH), start, end), dst, face(L_TAIL[i], UP), FLIP_V);
            copy(src, rows(face(M_TAIL, WEST), start, end), dst, face(L_TAIL[i], WEST), TRANSPOSE | FLIP_V);
            copy(src, rows(face(M_TAIL, EAST), start, end), dst, face(L_TAIL[i], EAST), TRANSPOSE | FLIP_U);
            copy(src, face(M_TAIL, DOWN), dst, face(L_TAIL[i], NORTH), 0);
            copy(src, face(M_TAIL, UP), dst, face(L_TAIL[i], SOUTH), 0);
        }

        leg(src, dst, L_BACK_LEFT_LEG, true);
        leg(src, dst, L_BACK_RIGHT_LEG, false);
        leg(src, dst, L_FRONT_LEFT_LEG, true);
        leg(src, dst, L_FRONT_RIGHT_LEG, false);
        return dst.image;
    }

    private static void leg(Sheet src, Sheet dst, int[][] parts, boolean mirror) {
        int op = mirror ? FLIP_U : 0;
        for (int i = 0; i < parts.length; i++) {
            for (int side = WEST; side <= SOUTH; side++) {
                int from = !mirror ? side : side == WEST ? EAST : side == EAST ? WEST : side;
                copy(src, rows(face(M_LEG, from), LEG_CUTS[i], LEG_CUTS[i + 1]), dst, face(parts[i], side), op);
            }
            double[] cap = rows(face(M_LEG, NORTH), LEG_CUTS[i], LEG_CUTS[i] + 1 / 11.0);
            copy(src, i == 0 ? face(M_LEG, DOWN) : cap, dst, face(parts[i], DOWN), op);
            copy(src, i == parts.length - 1 ? face(M_LEG, UP) : cap, dst, face(parts[i], UP), op);
        }
    }

    private static void box(Sheet src, int[] from, Sheet dst, int[] to) {
        for (int side = WEST; side <= UP; side++) copy(src, face(from, side), dst, face(to, side), 0);
    }

    private static double[] face(int[] box, int side) {
        int u = box[0];
        int v = box[1];
        int w = box[2];
        int h = box[3];
        int d = box[4];
        return switch (side) {
            case WEST -> new double[]{u, v + d, d, h};
            case NORTH -> new double[]{u + d, v + d, w, h};
            case EAST -> new double[]{u + d + w, v + d, d, h};
            case SOUTH -> new double[]{u + d + w + d, v + d, w, h};
            case DOWN -> new double[]{u + d, v, w, d};
            default -> new double[]{u + d + w, v, w, d};
        };
    }

    private static double[] rows(double[] rect, double start, double end) {
        return new double[]{rect[0], rect[1] + rect[3] * start, rect[2], rect[3] * (end - start)};
    }

    private static double[] columns(double[] rect, double start, double end) {
        return new double[]{rect[0] + rect[2] * start, rect[1], rect[2] * (end - start), rect[3]};
    }

    private static void copy(Sheet src, double[] from, Sheet dst, double[] to, int op) {
        int x0 = (int) Math.round(to[0] * dst.scale);
        int y0 = (int) Math.round(to[1] * dst.scale);
        int width = (int) Math.round(to[2] * dst.scale);
        int height = (int) Math.round(to[3] * dst.scale);
        int maxX = src.image.getWidth() - 1;
        int maxY = src.image.getHeight() - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x0 + x >= dst.image.getWidth() || y0 + y >= dst.image.getHeight()) continue;
                double fu = (x + 0.5) / width;
                double fv = (y + 0.5) / height;
                double a = (op & TRANSPOSE) != 0 ? fv : fu;
                double b = (op & TRANSPOSE) != 0 ? fu : fv;
                if ((op & FLIP_U) != 0) a = 1 - a;
                if ((op & FLIP_V) != 0) b = 1 - b;
                int sx = Math.max(0, Math.min(maxX, (int) Math.floor((from[0] + a * from[2]) * src.scale)));
                int sy = Math.max(0, Math.min(maxY, (int) Math.floor((from[1] + b * from[3]) * src.scale)));
                dst.image.setRGB(x0 + x, y0 + y, src.image.getRGB(sx, sy));
            }
        }
    }
}
