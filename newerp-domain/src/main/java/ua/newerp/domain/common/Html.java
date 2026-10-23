package ua.newerp.domain.common;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OpenCart stores some names with HTML in them (icons, &amp;quot;). This turns them into plain text. */
public final class Html {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("nbsp", " "), Map.entry("deg", "°"), Map.entry("laquo", "«"), Map.entry("raquo", "»"),
            Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("hellip", "…"), Map.entry("lsquo", "‘"),
            Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("bdquo", "„"),
            Map.entry("copy", "©"), Map.entry("reg", "®"), Map.entry("trade", "™"), Map.entry("times", "×"),
            Map.entry("middot", "·"), Map.entry("plusmn", "±"));

    private Html() {
    }

    /** Decodes entities, drops tags and collapses whitespace (like WebUtility.HtmlDecode + a regex in .NET). */
    public static String toPlainText(String html) {
        String decoded = decode(html == null ? "" : html);
        return SPACES.matcher(TAG.matcher(decoded).replaceAll("")).replaceAll(" ").strip();
    }

    public static String decode(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder result = new StringBuilder();
        while (m.find()) {
            String entity = m.group(1);
            String replacement;
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                replacement = Character.toString(Integer.parseInt(entity.substring(2), 16));
            } else if (entity.startsWith("#")) {
                replacement = Character.toString(Integer.parseInt(entity.substring(1)));
            } else {
                replacement = NAMED.getOrDefault(entity, m.group());
            }
            m.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        return m.appendTail(result).toString();
    }
}
