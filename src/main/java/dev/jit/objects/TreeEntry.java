package dev.jit.objects;

import dev.jit.util.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/** One line of a tree: a name, what kind of thing it is, and the id of its blob or subtree. */
public record TreeEntry(FileMode mode, String name, String id) {

    /**
     * Git's order: compare names as raw bytes, but pretend directories end in "/".
     * So "src-b" < "src.txt" < "src/" ('-' < '.' < '/'), even though plain "src" would sort first.
     * Getting this wrong still produces a valid-looking tree, just with a different id than git's.
     */
    public static final Comparator<TreeEntry> GIT_ORDER =
            (a, b) -> Arrays.compareUnsigned(a.sortKey(), b.sortKey());

    public TreeEntry {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\0"))
            throw new IllegalArgumentException("invalid tree entry name: '" + name + "'");
        if (!Hashing.isFullHex(id)) throw new IllegalArgumentException("invalid object id: " + id);
    }

    public boolean isTree() { return mode == FileMode.DIRECTORY; }

    /** The line cat-file -p and ls-tree print, e.g. "100644 blob 3b18e5...\thello.txt". */
    public String format(String path) {
        return mode.displayName() + " " + mode.objectType().wireName() + " " + id + "\t" + path;
    }

    private byte[] sortKey() {
        return (isTree() ? name + "/" : name).getBytes(StandardCharsets.UTF_8);
    }
}
