package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETNbt;
import com.slize.datarium.client.cet.CETSubject;
import com.slize.datarium.client.cet.CETUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class GenericProperties {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+|-\\d+)-(\\d+|-\\d+)");

    private GenericProperties() {}

    @Nullable
    public static Boolean readBoolean(Properties properties, int ruleNumber, String... ids) {
        for (String id : ids) {
            String key = id + "." + ruleNumber;
            if (properties.containsKey(key)) {
                String input = properties.getProperty(key).trim();
                if ("true".equals(input) || "false".equals(input)) return "true".equals(input);
                CETUtils.warn("properties files number error in " + id + " category");
            }
        }
        return null;
    }

    @Nullable
    public static Integer[] readIntegers(Properties properties, int ruleNumber, String... ids) {
        for (String id : ids) {
            if (id == null || id.isEmpty() || !properties.containsKey(id + "." + ruleNumber)) continue;
            String data = properties.getProperty(id + "." + ruleNumber).trim().replaceAll("[)(]", "");
            List<Integer> integers = new ArrayList<>();
            for (String token : data.split("\\s+")) {
                token = token.trim();
                if (token.replaceAll("\\D", "").isEmpty()) continue;
                try {
                    if (token.contains("-")) {
                        int[] range = intRange(token);
                        for (int i = range[0]; i <= range[1]; i++) integers.add(i);
                    } else {
                        integers.add(Integer.parseInt(token.replaceAll("\\D", "")));
                    }
                } catch (NumberFormatException e) {
                    CETUtils.warn("properties files number error in " + id + " category");
                    return null;
                }
            }
            return integers.toArray(new Integer[0]);
        }
        return null;
    }

    public static int[] intRange(String raw) {
        String digits = raw.trim().replaceAll("[^0-9-]", "");
        try {
            int a;
            int b;
            if (RANGE_PATTERN.matcher(digits).matches()) {
                String[] parts = digits.split("(?<!^|-)-");
                a = Integer.parseInt(parts[0]);
                b = Integer.parseInt(parts[1]);
            } else {
                a = b = Integer.parseInt(digits);
            }
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (Exception e) {
            CETUtils.error("Error parsing range: " + raw);
            return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
        }
    }

    public abstract static class BooleanProperty extends CETProperty {
        private final boolean value;

        protected BooleanProperty(@Nullable Boolean value) throws Invalid {
            if (value == null) throw new Invalid(getPropertyId() + " property was broken");
            this.value = value;
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            Boolean fromEntity = getValue(subject);
            return fromEntity != null && fromEntity == value;
        }

        @Nullable
        protected abstract Boolean getValue(CETSubject subject);
    }

    public abstract static class IntegerArrayProperty extends CETProperty {
        private final Set<Integer> values;

        protected IntegerArrayProperty(@Nullable Integer[] values) throws Invalid {
            if (values == null || values.length == 0) throw new Invalid(getPropertyId() + " property was broken");
            this.values = new HashSet<>(Arrays.asList(values));
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            return values.contains(getValue(subject));
        }

        protected abstract int getValue(CETSubject subject);
    }

    public abstract static class NumberRangeProperty<N extends Comparable<N>> extends CETProperty {
        protected final String originalInput;
        protected final boolean doPrint;
        private final List<Predicate<N>> ranges = new ArrayList<>();

        protected NumberRangeProperty(@Nullable String input) throws Invalid {
            if (input == null || input.trim().isEmpty()) throw new Invalid(getPropertyId() + " property was broken");
            originalInput = input;
            doPrint = input.startsWith("print:");
            String test = doPrint ? input.substring(6) : input;
            String[] tokens = test.replaceAll("[^0-9.\\s-]", "").trim().split("\\s+");
            if (tokens.length == 0) throw new Invalid(getPropertyId() + " property was broken");
            for (String token : tokens) {
                Predicate<N> range = parseRange(token);
                if (range != null) ranges.add(range);
            }
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            N value = getValue(subject);
            if (value != null) {
                for (Predicate<N> range : ranges) {
                    if (range.test(value)) {
                        if (doPrint) CETUtils.log(getPropertyId() + " property value print: [" + value + "], returned: true.");
                        return true;
                    }
                }
            }
            if (doPrint) CETUtils.log(getPropertyId() + " property value print: [" + value + "], returned: false.");
            return false;
        }

        @Nullable
        protected abstract Predicate<N> parseRange(String token);

        @Nullable
        protected abstract N getValue(CETSubject subject);
    }

    public abstract static class FloatRangeProperty extends NumberRangeProperty<Float> {
        protected FloatRangeProperty(@Nullable String input) throws Invalid {
            super(input);
        }

        @Nullable
        @Override
        protected Predicate<Float> parseRange(String token) {
            try {
                String[] parts = token.split("(?<!^|-)-");
                float left = Float.parseFloat(parts[0].replaceAll("[^0-9.-]", ""));
                float right = parts.length > 1 ? Float.parseFloat(parts[1].replaceAll("[^0-9.-]", "")) : left;
                float min = Math.min(left, right);
                float max = Math.max(left, right);
                return value -> value >= min && value <= max;
            } catch (Exception e) {
                CETUtils.error("number or range in [" + getPropertyId() + "] property could not be extracted from input: " + token);
                return null;
            }
        }
    }

    public abstract static class LongRangeProperty extends NumberRangeProperty<Long> {
        protected LongRangeProperty(@Nullable String input) throws Invalid {
            super(input);
        }

        @Nullable
        @Override
        protected Predicate<Long> parseRange(String token) {
            try {
                String[] parts = token.split("(?<!^|-)-");
                long left = Long.parseLong(parts[0].replaceAll("[^0-9-]", ""));
                long right = parts.length > 1 ? Long.parseLong(parts[1].replaceAll("[^0-9-]", "")) : left;
                long min = Math.min(left, right);
                long max = Math.max(left, right);
                return value -> value >= min && value <= max;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public abstract static class StringArrayOrRegexProperty extends CETProperty {
        protected final String originalInput;
        protected final Set<String> values;
        protected final Predicate<String> matcher;
        protected final boolean usesRegex;
        protected final boolean doPrint;

        protected StringArrayOrRegexProperty(@Nullable String input) throws Invalid {
            if (input == null || input.trim().isEmpty()) throw new Invalid(getPropertyId() + " property was broken");
            originalInput = input;
            doPrint = input.startsWith("print:");
            String test = doPrint ? input.substring(6) : input;
            if (test.startsWith("regex:") || test.startsWith("pattern:") || test.startsWith("iregex:") || test.startsWith("ipattern:")) {
                Predicate<String> compiled = CETNbt.stringMatcher(test);
                if (compiled == null) throw new Invalid(getPropertyId() + " property was broken");
                matcher = compiled;
                values = Collections.singleton(test);
                usesRegex = true;
            } else {
                String[] tokens = test.trim().split("\\s+");
                if (tokens.length == 0) throw new Invalid(getPropertyId() + " property was broken");
                values = new HashSet<>();
                for (String token : tokens) values.add(forceLowerCase() ? token.toLowerCase() : token);
                if (tokens.length != 1) values.add(test.trim());
                matcher = this::testArray;
                usesRegex = false;
            }
        }

        private boolean testArray(String fromEntity) {
            boolean matches = false;
            for (String value : values) {
                if (value.startsWith("!")) {
                    matches = true;
                    if (value.substring(1).equals(fromEntity)) return false;
                } else if (value.equals(fromEntity)) {
                    return true;
                }
            }
            return matches;
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            String value = getValue(subject);
            if (value != null) {
                boolean result = matcher.test(forceLowerCase() ? value.toLowerCase() : value);
                if (doPrint) CETUtils.log(getPropertyId() + " property value print: [" + value + "], returned: " + result + ", can update: " + canPropertyUpdate());
                return result;
            }
            if (doPrint) CETUtils.log(getPropertyId() + " property value print: [<null>], returned: false, can update: " + canPropertyUpdate());
            return false;
        }

        protected abstract boolean forceLowerCase();

        @Nullable
        protected abstract String getValue(CETSubject subject);
    }
}
