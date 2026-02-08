package com.slize.datarium.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Json5Helper {
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*");

    public static String cleanJson5(String json) {
        if (json == null) return "";
        Matcher matcher = BLOCK_COMMENT.matcher(json);
        String noBlock = matcher.replaceAll("");
        Matcher matcher2 = LINE_COMMENT.matcher(noBlock);
        return matcher2.replaceAll("");
    }
}
