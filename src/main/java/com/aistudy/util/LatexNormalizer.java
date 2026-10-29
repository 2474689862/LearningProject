package com.aistudy.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 公式文本规范化：把模型转写里常见的不规范写法改成 KaTeX 能正确渲染的 LaTeX。
 * <p>只处理数学区段（$...$、$$...$$、\(...\)、\[...\]）内部，区间外的中文与普通文字保持原样。
 * 主要修三类问题：Unicode 上下标（x²）、Unicode 根号（√2）、Unicode 运算符号（× ÷ ≤ ≠ →）。</p>
 */
public final class LatexNormalizer {

    private static final Map<Character, String> SUPERSCRIPT = new HashMap<>();

    static {
        put('⁰', "0");
        put('¹', "1");
        put('²', "2");
        put('³', "3");
        put('⁴', "4");
        put('⁵', "5");
        put('⁶', "6");
        put('⁷', "7");
        put('⁸', "8");
        put('⁹', "9");
        put('⁻', "-");
        put('⁺', "+");
        put('ⁿ', "n");
    }

    private static void put(char c, String s) {
        SUPERSCRIPT.put(c, s);
    }

    private LatexNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (c == '$') {
                boolean display = i + 1 < len && text.charAt(i + 1) == '$';
                String delim = display ? "$$" : "$";
                int start = i + delim.length();
                int end = text.indexOf(delim, start);
                if (end < 0) {
                    out.append(text, i, len);
                    break;
                }
                out.append(delim).append(fixMath(text.substring(start, end))).append(delim);
                i = end + delim.length();
            } else if (c == '\\' && i + 1 < len && (text.charAt(i + 1) == '(' || text.charAt(i + 1) == '[')) {
                String close = text.charAt(i + 1) == '(' ? "\\)" : "\\]";
                int start = i + 2;
                int end = text.indexOf(close, start);
                if (end < 0) {
                    out.append(text, i, len);
                    break;
                }
                out.append(text, i, start).append(fixMath(text.substring(start, end))).append(close);
                i = end + close.length();
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 数学区段内部的逐项替换 */
    private static String fixMath(String math) {
        // 模型偶尔写成 \√2、\×，反斜杠在这里没有意义，先去掉
        String s = math.replaceAll("\\\\([√×÷≤≥≠≈±∞→∈∪∩·°])", "$1");
        s = superscripts(s);
        s = sqrt(s);
        s = fraction(s);
        s = symbols(s);
        return s;
    }

    private static final java.util.regex.Pattern SIMPLE_FRACTION = java.util.regex.Pattern.compile(
            "([A-Za-z0-9]+(?:\\^\\{[^}]*\\}|_\\{[^}]*\\})?)/([A-Za-z0-9]+(?:\\^\\{[^}]*\\}|_\\{[^}]*\\})?)");

    /** x^{2}/a^{2} -> \frac{x^{2}}{a^{2}}；两侧必须是简单项，避免误伤复杂表达式 */
    private static String fraction(String s) {
        if (s.indexOf('/') < 0 || s.contains("\\frac")) {
            return s;
        }
        return SIMPLE_FRACTION.matcher(s).replaceAll("\\\\frac{$1}{$2}");
    }

    /** x² -> x^{2}，a⁻¹ -> a^{-1}，连续的 Unicode 上标合并成一组 */
    private static String superscripts(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        int i = 0;
        while (i < s.length()) {
            String rep = SUPERSCRIPT.get(s.charAt(i));
            if (rep == null) {
                b.append(s.charAt(i));
                i++;
                continue;
            }
            StringBuilder group = new StringBuilder(rep);
            int j = i + 1;
            while (j < s.length()) {
                String next = SUPERSCRIPT.get(s.charAt(j));
                if (next == null) {
                    break;
                }
                group.append(next);
                j++;
            }
            b.append("^{").append(group).append('}');
            i = j;
        }
        return b.toString();
    }

    /** √2 -> \sqrt{2}，√x -> \sqrt{x}，√(a+b) -> \sqrt{a+b}，√{2} -> \sqrt{2} */
    private static String sqrt(String s) {
        if (s.indexOf('√') < 0) {
            return s;
        }
        StringBuilder b = new StringBuilder(s.length() + 8);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '√') {
                b.append(c);
                i++;
                continue;
            }
            int j = i + 1;
            if (j < s.length() && s.charAt(j) == '{') {
                b.append("\\sqrt");
                i = j;
                continue;
            }
            if (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                int k = j;
                while (k < s.length() && (Character.isDigit(s.charAt(k)) || s.charAt(k) == '.')) {
                    k++;
                }
                b.append("\\sqrt{").append(s, j, k).append('}');
                i = k;
                continue;
            }
            if (j < s.length() && Character.isLetter(s.charAt(j)) && s.charAt(j) < 128) {
                b.append("\\sqrt{").append(s.charAt(j)).append('}');
                i = j + 1;
                continue;
            }
            if (j < s.length() && s.charAt(j) == '(') {
                int depth = 1;
                int k = j + 1;
                while (k < s.length() && depth > 0) {
                    char x = s.charAt(k);
                    if (x == '(') {
                        depth++;
                    } else if (x == ')') {
                        depth--;
                    }
                    k++;
                }
                if (depth == 0) {
                    b.append("\\sqrt{").append(s, j + 1, k - 1).append('}');
                    i = k;
                    continue;
                }
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    /** Unicode 运算符号替换成 LaTeX 命令 */
    private static String symbols(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '×':
                    b.append("\\times ");
                    break;
                case '÷':
                    b.append("\\div ");
                    break;
                case '·':
                    b.append("\\cdot ");
                    break;
                case '≤':
                    b.append("\\leq ");
                    break;
                case '≥':
                    b.append("\\geq ");
                    break;
                case '≠':
                    b.append("\\neq ");
                    break;
                case '≈':
                    b.append("\\approx ");
                    break;
                case '±':
                    b.append("\\pm ");
                    break;
                case '∞':
                    b.append("\\infty ");
                    break;
                case '→':
                    b.append("\\to ");
                    break;
                case '∈':
                    b.append("\\in ");
                    break;
                case '∪':
                    b.append("\\cup ");
                    break;
                case '∩':
                    b.append("\\cap ");
                    break;
                case '°':
                    b.append("^{\\circ}");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }
}
