package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.Status;
import dev.jit.core.Status.Change;
import dev.jit.index.Index;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * jit status [-s | --porcelain]
 *   long (default): sections with explanations, paths relative to where you are
 *   -s:             one line per path, "XY path" (X = staged, Y = unstaged, ?? = untracked)
 *   --porcelain:    like -s but always repo-relative paths: stable for scripts
 */
public final class StatusCommand implements Command {
    public String name()  { return "status"; }
    public String usage() { return "[-s|--porcelain]  show staged, unstaged and untracked changes"; }

    public int run(String[] args) throws Exception {
        String format = "long";
        for (String a : args) {
            switch (a) {
                case "-s", "--short" -> format = "short";
                case "--porcelain"   -> format = "porcelain";
                default -> throw new IllegalArgumentException("unknown option " + a);
            }
        }
        Repository repo = Repository.findFromCwd();
        Status.Result r = Status.compute(repo);
        System.out.print(format.equals("long") ? longFormat(repo, r) : shortFormat(repo, r, format.equals("porcelain")));
        return 0;
    }

    private static String shortFormat(Repository repo, Status.Result r, boolean porcelain) {
        StringBuilder out = new StringBuilder();
        TreeSet<String> changed = new TreeSet<>(Index.PATH_ORDER);
        changed.addAll(r.staged().keySet());
        changed.addAll(r.unstaged().keySet());
        for (String p : changed) {
            out.append(code(r.staged(), p)).append(code(r.unstaged(), p)).append(' ')
               .append(porcelain ? p : repo.displayPath(p)).append('\n');
        }
        for (String p : r.untracked()) out.append("?? ").append(porcelain ? p : repo.displayPath(p)).append('\n');
        return out.toString();
    }

    private static char code(SortedMap<String, Change> changes, String path) {
        Change c = changes.get(path);
        return c == null ? ' ' : c.code;
    }

    private static String longFormat(Repository repo, Status.Result r) throws IOException {
        StringBuilder out = new StringBuilder();
        Optional<String> branch = repo.refs().readSymbolic("HEAD");
        Optional<String> head = repo.refs().read("HEAD");
        if (branch.isPresent()) out.append("On branch ").append(branch.get().replaceFirst("^refs/heads/", "")).append('\n');
        else out.append("HEAD detached at ").append(head.orElseThrow(), 0, 7).append('\n');
        if (head.isEmpty()) out.append("\nNo commits yet\n");
        out.append('\n');

        section(out, repo, "Changes to be committed:", null, r.staged());
        section(out, repo, "Changes not staged for commit:", "jit add <file>...\" to update what will be committed", r.unstaged());
        if (!r.untracked().isEmpty()) {
            out.append("Untracked files:\n  (use \"jit add <file>...\" to include in what will be committed)\n");
            for (String p : r.untracked()) out.append('\t').append(repo.displayPath(p)).append('\n');
            out.append('\n');
        }

        if (!r.staged().isEmpty()) return out.toString();
        if (!r.unstaged().isEmpty()) out.append("no changes added to commit (use \"jit add\")\n");
        else if (!r.untracked().isEmpty()) out.append("nothing added to commit but untracked files present (use \"jit add\" to track)\n");
        else if (head.isEmpty()) out.append("nothing to commit (create/copy files and use \"jit add\" to track)\n");
        else out.append("nothing to commit, working tree clean\n");
        return out.toString();
    }

    private static void section(StringBuilder out, Repository repo, String title, String hint, SortedMap<String, Change> changes) {
        if (changes.isEmpty()) return;
        out.append(title).append('\n');
        if (hint != null) out.append("  (use \"").append(hint).append(")\n");
        for (Map.Entry<String, Change> e : changes.entrySet())
            out.append('\t').append(String.format("%-12s", e.getValue().label)).append(repo.displayPath(e.getKey())).append('\n');
        out.append('\n');
    }
}
