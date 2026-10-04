package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.RevWalk;
import dev.jit.core.Revision;
import dev.jit.objects.Commit;
import dev.jit.objects.Signature;
import dev.jit.storage.Refs;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** jit log [--oneline] [-n <count>] [--decorate | --no-decorate] [<rev>] */
public final class LogCommand implements Command {
    public String name()  { return "log"; }
    public String usage() { return "[--oneline] [-n <count>] [<rev>]  show commit history"; }

    // "Wed Nov 15 03:48:20 2023" (+ offset added separately): git's default date format
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    public int run(String[] args) throws Exception {
        boolean oneline = false;
        Boolean decorate = null;                                           // null = auto: only when printing to a terminal
        long limit = Long.MAX_VALUE;
        String rev = "HEAD";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--oneline"     -> oneline = true;
                case "--decorate"    -> decorate = true;
                case "--no-decorate" -> decorate = false;
                case "-n" -> {
                    if (i + 1 == args.length) throw new IllegalArgumentException("option -n needs a value");
                    limit = Long.parseLong(args[++i]);
                }
                default -> {
                    if (a.matches("-\\d+")) limit = Long.parseLong(a.substring(1));   // -3 means -n 3
                    else if (a.startsWith("-")) throw new IllegalArgumentException("unknown option " + a);
                    else rev = a;
                }
            }
        }

        Repository repo = Repository.findFromCwd();
        if (rev.equals("HEAD") && repo.refs().read("HEAD").isEmpty()) {
            String branch = repo.refs().readSymbolic("HEAD").orElse("HEAD").replaceFirst("^refs/heads/", "");
            throw new IllegalStateException("your current branch '" + branch + "' does not have any commits yet");
        }
        Map<String, String> labels = (decorate != null ? decorate : System.console() != null)
                ? decorations(repo.refs()) : Map.of();

        RevWalk walk = new RevWalk(repo.objects(), Revision.resolve(repo, rev));
        StringBuilder out = new StringBuilder();
        RevWalk.Entry e;
        for (long shown = 0; shown < limit && (e = walk.next()) != null; shown++) {
            String label = labels.containsKey(e.id()) ? " (" + labels.get(e.id()) + ")" : "";
            if (oneline) {
                out.append(e.id(), 0, 7).append(label).append(' ').append(e.commit().subject()).append('\n');
            } else {
                if (shown > 0) out.append('\n');                           // blank line between commits
                medium(out, e.id(), e.commit(), label);
            }
        }
        System.out.print(out);
        return 0;
    }

    /** git's default "medium" format. */
    private static void medium(StringBuilder out, String id, Commit c, String label) {
        out.append("commit ").append(id).append(label).append('\n');
        if (c.parents().size() > 1) {
            out.append("Merge:");
            for (String p : c.parents()) out.append(' ').append(p, 0, 7);
            out.append('\n');
        }
        out.append("Author: ").append(c.author().name()).append(" <").append(c.author().email()).append(">\n");
        out.append("Date:   ").append(formatDate(c.author())).append("\n\n");
        for (String line : c.message().stripTrailing().split("\n", -1))   // every line indented, blank ones too
            out.append("    ").append(line).append('\n');
    }

    /** The time on the author's clock, with their offset: "Wed Nov 15 03:48:20 2023 +0530". */
    static String formatDate(Signature s) {
        return DATE.format(Instant.ofEpochSecond(s.epochSeconds()).atOffset(s.offset()))
                + " " + Signature.formatOffset(s.offset());
    }

    /**
     * Ref names to show next to each commit id, e.g. "HEAD -> main, tag: v1, side".
     * Git's order: HEAD first (merged with the branch it's on), then the rest in reverse name order.
     */
    static Map<String, String> decorations(Refs refs) throws IOException {
        Map<String, List<String>> byId = new HashMap<>();
        Optional<String> head = refs.read("HEAD");
        Optional<String> headBranch = refs.readSymbolic("HEAD");
        head.ifPresent(id -> byId.computeIfAbsent(id, k -> new ArrayList<>())
                .add(headBranch.map(b -> "HEAD -> " + shortName(b)).orElse("HEAD")));

        List<Map.Entry<String, String>> all = new ArrayList<>(refs.listAll().entrySet());
        for (Map.Entry<String, String> r : all.reversed()) {
            if (headBranch.isPresent() && r.getKey().equals(headBranch.get())) continue;   // already in "HEAD -> main"
            byId.computeIfAbsent(r.getValue(), k -> new ArrayList<>()).add(shortName(r.getKey()));
        }
        Map<String, String> labels = new HashMap<>();
        byId.forEach((id, names) -> labels.put(id, String.join(", ", names)));
        return labels;
    }

    private static String shortName(String ref) {
        if (ref.startsWith("refs/heads/"))   return ref.substring("refs/heads/".length());
        if (ref.startsWith("refs/tags/"))    return "tag: " + ref.substring("refs/tags/".length());
        if (ref.startsWith("refs/remotes/")) return ref.substring("refs/remotes/".length());
        return ref;
    }
}
