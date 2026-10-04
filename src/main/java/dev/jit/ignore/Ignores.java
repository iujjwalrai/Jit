package dev.jit.ignore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which untracked files to leave alone. Rules come from, lowest priority first:
 *   .jit/info/exclude          personal rules, not committed
 *   .jitignore in the root
 *   .jitignore in each subfolder (applies only inside that folder)
 * The last rule that matches wins, so deeper files override shallower ones and "!x" can undo "*".
 * Ignoring only affects untracked files: once a file is tracked, changes to it always show up.
 */
public final class Ignores {
    public static final String FILE_NAME = ".jitignore";

    private final Path root;
    private final List<IgnoreRule> exclude;
    private final Map<String, List<IgnoreRule>> byDir = new HashMap<>();   // folder -> its .jitignore rules, loaded once

    private Ignores(Path root, List<IgnoreRule> exclude) {
        this.root = root;
        this.exclude = exclude;
    }

    public static Ignores load(Path root, Path jitDir) throws IOException {
        return new Ignores(root, readRules(jitDir.resolve("info/exclude")));
    }

    /** Ignores nothing. */
    public static Ignores none() { return new Ignores(null, List.of()); }

    /**
     * Is this exact path ignored by a rule? Doesn't look at parent folders: walkers skip an ignored
     * folder anyway, so they never ask about what's inside it.
     */
    public boolean matches(String path, boolean isDir) {
        Boolean verdict = null;
        for (IgnoreRule r : exclude) if (r.matches(path, isDir)) verdict = !r.negated();
        String dir = "";
        while (true) {                                                   // root .jitignore, then each folder down to ours
            String rel = dir.isEmpty() ? path : path.substring(dir.length() + 1);
            for (IgnoreRule r : rulesIn(dir)) if (r.matches(rel, isDir)) verdict = !r.negated();
            int next = path.indexOf('/', dir.isEmpty() ? 0 : dir.length() + 1);
            if (next < 0) break;
            dir = path.substring(0, next);
        }
        return verdict != null && verdict;
    }

    /** Is this path ignored, either itself or because a folder above it is? (e.g. build/x.o under build/) */
    public boolean isIgnored(String path, boolean isDir) {
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1))
            if (matches(path.substring(0, slash), true)) return true;
        return matches(path, isDir);
    }

    private List<IgnoreRule> rulesIn(String dir) {
        if (root == null) return List.of();                              // none()
        return byDir.computeIfAbsent(dir, d -> {
            try {
                return readRules((d.isEmpty() ? root : root.resolve(d)).resolve(FILE_NAME));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static List<IgnoreRule> readRules(Path file) throws IOException {
        List<IgnoreRule> rules = new ArrayList<>();
        if (!Files.isRegularFile(file)) return rules;
        for (String line : Files.readAllLines(file)) IgnoreRule.parse(line).ifPresent(rules::add);
        return rules;
    }
}
