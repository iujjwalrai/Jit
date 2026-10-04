package dev.jit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of .jit/config, which uses git's INI-style format:
 *   [user]
 *       name = Ujjwal Rai
 * Keys are looked up as "section.key", e.g. "user.name". Section and key names ignore case, like git.
 */
public final class Config {
    private final Map<String, String> values;

    private Config(Map<String, String> values) { this.values = values; }

    public static Config load(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(file)) return new Config(values);

        String section = "";
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                // [core] -> "core";  [branch "main"] -> "branch.main" (subsection keeps its case)
                String inner = line.substring(1, line.length() - 1).trim();
                int quote = inner.indexOf('"');
                section = quote < 0
                        ? inner.toLowerCase()
                        : inner.substring(0, quote).trim().toLowerCase() + "." + inner.substring(quote + 1, inner.lastIndexOf('"'));
                continue;
            }
            int eq = line.indexOf('=');
            String key = (eq < 0 ? line : line.substring(0, eq)).trim().toLowerCase();
            String value = eq < 0 ? "true" : unquote(line.substring(eq + 1).trim());   // bare "key" means true
            values.put(section + "." + key, value);
        }
        return new Config(values);
    }

    public Optional<String> get(String key) {
        int dot = key.lastIndexOf('.');
        String section = key.substring(0, dot), name = key.substring(dot + 1);
        int sub = section.indexOf('.');                         // lowercase everything except a subsection
        section = sub < 0 ? section.toLowerCase() : section.substring(0, sub).toLowerCase() + section.substring(sub);
        return Optional.ofNullable(values.get(section + "." + name.toLowerCase()));
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }
}
