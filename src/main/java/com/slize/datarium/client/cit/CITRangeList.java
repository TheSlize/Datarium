package com.slize.datarium.client.cit;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class CITRangeList {
    private final int[] mins;
    private final int[] maxs;

    private CITRangeList(int[] mins, int[] maxs) {
        this.mins = mins;
        this.maxs = maxs;
    }

    public boolean contains(int value) {
        for (int i = 0; i < mins.length; i++) {
            if (value >= mins[i] && value <= maxs[i]) return true;
        }
        return false;
    }

    @Nullable
    public static CITRangeList parse(@Nullable String str) {
        return parse(str, null);
    }

    @Nullable
    public static CITRangeList parse(@Nullable String str, @Nullable ToIntFunction<String> named) {
        if (str == null) return null;
        List<int[]> ranges = new ArrayList<>();
        for (String token : str.trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            if (named != null) {
                int value = named.applyAsInt(token);
                if (value != Integer.MIN_VALUE) {
                    ranges.add(new int[]{value, value});
                    continue;
                }
            }
            int[] range = parseRange(token);
            if (range == null) return null;
            ranges.add(range);
        }
        if (ranges.isEmpty()) return null;
        int[] mins = new int[ranges.size()];
        int[] maxs = new int[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) {
            mins[i] = ranges.get(i)[0];
            maxs[i] = ranges.get(i)[1];
        }
        return new CITRangeList(mins, maxs);
    }

    @Nullable
    private static int[] parseRange(String token) {
        int dash = token.indexOf('-');
        if (dash != token.lastIndexOf('-')) return null;
        try {
            if (dash < 0) {
                int value = Integer.parseInt(token);
                return value < 0 ? null : new int[]{value, value};
            }
            String lo = token.substring(0, dash).trim();
            String hi = token.substring(dash + 1).trim();
            if (lo.isEmpty() && hi.isEmpty()) return null;
            int min = lo.isEmpty() ? 0 : Integer.parseInt(lo);
            int max = hi.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(hi);
            if (min < 0 || max < 0) return null;
            return new int[]{Math.min(min, max), Math.max(min, max)};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
