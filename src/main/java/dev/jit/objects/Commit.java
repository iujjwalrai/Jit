package dev.jit.objects;

import dev.jit.util.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot plus history. Unlike a tree, the body is plain text:
 *   tree 68aba62e...
 *   parent d0d854d9...        (0 for the first commit, 1 normally, 2+ for a merge)
 *   author Ujjwal Rai <ujjwal@example.com> 1700000000 +0530
 *   committer Ujjwal Rai <ujjwal@example.com> 1700000000 +0530
 *
 *   the message
 */
public record Commit(String tree, List<String> parents, Signature author, Signature committer, String message)
        implements GitObject {

    public Commit {
        if (!Hashing.isFullHex(tree)) throw new IllegalArgumentException("invalid tree id: " + tree);
        for (String p : parents)
            if (!Hashing.isFullHex(p)) throw new IllegalArgumentException("invalid parent id: " + p);
        parents = List.copyOf(parents);
    }

    @Override public ObjectType type() { return ObjectType.COMMIT; }

    @Override public byte[] body() {
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(tree).append('\n');
        for (String p : parents) sb.append("parent ").append(p).append('\n');
        sb.append("author ").append(author.format()).append('\n');
        sb.append("committer ").append(committer.format()).append('\n');
        sb.append('\n').append(message);                       // blank line separates headers from message
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The first paragraph on one line: what log --oneline and commit print. */
    public String subject() {
        StringBuilder sb = new StringBuilder();
        for (String line : message.split("\n")) {
            if (line.isBlank()) { if (!sb.isEmpty()) break; else continue; }   // skip leading blanks, stop at the first gap
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(line);
        }
        return sb.toString();
    }

    public static Commit parse(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        int split = text.indexOf("\n\n");                       // first blank line ends the headers
        if (split < 0) throw new IllegalStateException("corrupt commit: no blank line after headers");

        String tree = null;
        List<String> parents = new ArrayList<>();
        Signature author = null, committer = null;
        for (String line : text.substring(0, split).split("\n")) {
            if (line.startsWith(" ")) continue;                 // continuation of a multi-line header (e.g. gpgsig)
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            String value = space < 0 ? "" : line.substring(space + 1);
            switch (key) {
                case "tree"      -> tree = value;
                case "parent"    -> parents.add(value);
                case "author"    -> author = Signature.parse(value);
                case "committer" -> committer = Signature.parse(value);
                default          -> { }                         // gpgsig, encoding, ...: not needed yet
            }
        }
        if (tree == null || author == null || committer == null)
            throw new IllegalStateException("corrupt commit: missing tree, author or committer");
        return new Commit(tree, parents, author, committer, text.substring(split + 2));
    }
}
