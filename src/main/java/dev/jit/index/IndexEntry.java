package dev.jit.index;

import dev.jit.objects.FileMode;
import dev.jit.util.Hashing;

/**
 * One staged file: which blob it is, plus a snapshot of the file's metadata at the time it was added.
 * The metadata (stat) is a speed trick: if a file's size and timestamps still match, git assumes the
 * content hasn't changed and skips re-hashing it. That's how `git status` stays fast on big repos.
 */
public record IndexEntry(String path, FileMode mode, String id, Stat stat) {

    public IndexEntry {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//") || path.contains("\0"))
            throw new IllegalArgumentException("invalid index path: '" + path + "'");
        for (String part : path.split("/"))
            if (part.equals(".") || part.equals("..") || part.equals(".git") || part.equals(".jit"))
                throw new IllegalArgumentException("invalid index path: '" + path + "'");
        if (mode == FileMode.DIRECTORY) throw new IllegalArgumentException("directories aren't stored in the index: " + path);
        if (!Hashing.isFullHex(id)) throw new IllegalArgumentException("invalid object id: " + id);
    }

    /**
     * The file metadata git stores per entry, each squeezed into 32 bits (big values just wrap,
     * which is fine: it's only compared for equality).
     */
    public record Stat(int ctimeSec, int ctimeNsec, int mtimeSec, int mtimeNsec,
                       int dev, int ino, int uid, int gid, int size) {
        public static final Stat ZERO = new Stat(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
