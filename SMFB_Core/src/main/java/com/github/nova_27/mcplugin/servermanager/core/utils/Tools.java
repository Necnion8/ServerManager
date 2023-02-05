package com.github.nova_27.mcplugin.servermanager.core.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * ツール
 */
public class Tools {
    /**
     * 文字列の{0},{1},{2},...をargsで置き換える
     * @param original もとの文字
     * @param args 置き換えする文字列
     * @return 置き換えた文字列
     */
    public static String Formatter(String original, String... args) {
        int i = 0;
        for(String arg : args) {
            original = original.replace("{"+i+"}", arg);
            i++;
        }
        return original;
    }

    /**
     * https://gist.github.com/raymyers/8077031
     * add custom fix
     */
    public static List<String> parseCommandArguments(CharSequence string) {
        List<String> tokens = new ArrayList<>();
        boolean escaping = false;
        char quoteChar = ' ';
        boolean quoting = false;
        int lastCloseQuoteIndex = Integer.MIN_VALUE;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i<string.length(); i++) {
            char c = string.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
            } else if (c == '\\' && (!quoting && (quoteChar == '\'' || quoteChar == '"'))) {
                escaping = true;
            } else if (quoting && c == quoteChar) {
                quoting = false;
                lastCloseQuoteIndex = i;
            } else if (!quoting && (c == '\'' || c == '"')) {
                quoting = true;
                quoteChar = c;
            } else if (!quoting && Character.isWhitespace(c)) {
                if (current.length() > 0 || lastCloseQuoteIndex == (i - 1)) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || lastCloseQuoteIndex == (string.length() - 1)) {
            tokens.add(current.toString());
        }

        return tokens;
    }

}
