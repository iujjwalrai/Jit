package dev.jit.ignore;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One line of a .jitignore file (same syntax as .gitignore):
 *   *.log          any file named *.log, in any folder
 *   /b.txt         only b.txt next to this .jitignore (leading / = anchored here)
 *   build/         folders only (trailing /)
 *   docs/*.md      a slash in the middle also anchors it: docs/ next to this .jitignore
 *   **&#47;foo, a/**, a/**&#47;b    ** spans any number of folders
 *   !keep.log      negation: un-ignore something an earlier line ignored
 */
record IgnoreRule(Pattern regex, boolean negated, boolean dirOnly, boolean anchored) {

    /** Parse one line; empty for blank lines and comments. */
    static Optional<IgnoreRule> parse(String line) {
        String p = stripTrailingSpaces(line);
        if (p.isEmpty() || p.startsWith("#")) return Optional.empty();
        boolean negated = p.startsWith("!");
        if (negated) p = p.substring(1);
        else if (p.startsWith("\\!") || p.startsWith("\\#")) p = p.substring(1);   // "\!x" = a file literally named "!x"

        boolean dirOnly = p.endsWith("/");
        if (dirOnly) p = p.substring(0, p.length() - 1);
        boolean anchored = p.contains("/");                       // any slash left (leading or middle) anchors it
        if (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return Optional.empty();
        return Optional.of(new IgnoreRule(Pattern.compile(toRegex(p)), negated, dirOnly, anchored));
    }

    /** `path` is relative to the folder holding this rule's .jitignore. */
    boolean matches(String path, boolean isDir) {
        if (dirOnly && !isDir) return false;
        String subject = anchored ? path : path.substring(path.lastIndexOf('/') + 1);   // unanchored: just the name
        return regex.matcher(subject).matches();
    }

    /** Glob -> regex. `*` and `?` never cross a "/", but `**` between slashes spans any number of folders. */
    static String toRegex(String glob) {
        StringBuilder re = new StringBuilder();
        int n = glob.length();
        for (int i = 0; i < n; i++) {
            char c = glob.charAt(i);
            if (glob.startsWith("**/", i) && i == 0) {                     // "**/x": x in any folder, including the top
                re.append("(?:.*/)?");
                i += 2;
            } else if (glob.startsWith("/**/", i)) {                      // "a/**/b": zero or more folders between
                re.append("/(?:.*/)?");
                i += 3;
            } else if (glob.startsWith("/**", i) && i + 3 == n) {         // "a/**": everything inside a
                re.append("/.*");
                i += 2;
            } else if (c == '*') {
                while (i + 1 < n && glob.charAt(i + 1) == '*') i++;       // other "**" acts like "*"
                re.append("[^/]*");
            } else if (c == '?') {
                re.append("[^/]");
            } else if (c == '[') {
                int end = glob.indexOf(']', i + 2);                       // "[]a]" : a ] right after [ is literal
                if (end < 0) { re.append("\\["); continue; }
                String body = glob.substring(i + 1, end);
                if (body.startsWith("!")) body = "^" + body.substring(1); // [!abc] = not a, b or c
                re.append('[').append(body.replace("\\", "\\\\").replace("[", "\\[")).append(']');
                i = end;
            } else if (c == '\\' && i + 1 < n) {
                re.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
            } else {
                re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return re.toString();
    }

    /** Trailing spaces are ignored unless escaped with a backslash. */
    private static String stripTrailingSpaces(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ' && !(end > 1 && s.charAt(end - 2) == '\\')) end--;
        return s.substring(0, end);
    }
}
