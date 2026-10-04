package dev.jit.core;

import dev.jit.ignore.Ignores;
import dev.jit.index.Index;
import dev.jit.index.IndexEntry;
import dev.jit.objects.Blob;
import dev.jit.objects.FileMode;
import dev.jit.storage.ObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The files you actually edit, as opposed to what's staged or committed.
 * Paths are always relative to the repo root with "/" separators, e.g. "src/Main.java".
 */
public final class WorkTree {
    private static final Set<String> SKIP = Set.of(Repository.DIR_NAME, ".git");   // never track repo metadata
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;

    /** Something inside a folder: a file (regular or symlink) or a subfolder. */
    public record Child(String path, boolean isDir) {}

    private final Path root;

    public WorkTree(Path root) { this.root = root; }

    public boolean exists(String path) { return Files.exists(resolve(path), NOFOLLOW); }

    /** A file git can track: a regular file or a symlink (never followed). */
    public boolean isFile(String path) {
        Path p = resolve(path);
        return Files.isSymbolicLink(p) || Files.isRegularFile(p, NOFOLLOW);
    }

    /** What's directly inside a folder, skipping .jit/.git and special files (sockets, fifos). */
    public List<Child> children(String dir) throws IOException {
        List<Path> paths;
        try (Stream<Path> s = Files.list(resolve(dir))) { paths = s.toList(); }
        List<Child> out = new ArrayList<>();
        for (Path p : paths) {
            String name = p.getFileName().toString();
            if (SKIP.contains(name)) continue;
            String rel = dir.isEmpty() ? name : dir + "/" + name;
            if (Files.isSymbolicLink(p) || Files.isRegularFile(p, NOFOLLOW)) out.add(new Child(rel, false));
            else if (Files.isDirectory(p, NOFOLLOW)) out.add(new Child(rel, true));
        }
        return out;
    }

    /** Every file at or under `path` ("" = the whole tree), ignoring nothing. */
    public List<String> listFiles(String path) throws IOException {
        return listFiles(path, new Index(), Ignores.none());
    }

    /**
     * Every file at or under `path` that `add` should look at: tracked files always, untracked ones only
     * if not ignored. Ignored folders with nothing tracked inside (like target/) aren't even opened.
     */
    public List<String> listFiles(String path, Index index, Ignores ignores) throws IOException {
        for (String part : path.split("/"))
            if (SKIP.contains(part)) throw new IllegalArgumentException("'" + path + "' is inside repository metadata");
        List<String> out = new ArrayList<>();
        if (isFile(path)) {
            if (index.get(path) != null || !ignores.isIgnored(path, false)) out.add(path);
        } else if (Files.isDirectory(resolve(path), NOFOLLOW)) {
            walk(path, !path.isEmpty() && ignores.isIgnored(path, true), index, ignores, out);
        }
        return out;
    }

    private void walk(String dir, boolean dirIgnored, Index index, Ignores ignores, List<String> out) throws IOException {
        for (Child c : children(dir)) {
            if (!c.isDir()) {
                boolean ignored = dirIgnored || ignores.matches(c.path(), false);
                if (index.get(c.path()) != null || !ignored) out.add(c.path());
            } else {
                boolean ignored = dirIgnored || ignores.matches(c.path(), true);
                if (ignored && index.pathsUnder(c.path()).isEmpty()) continue;   // nothing tracked in there: skip it all
                walk(c.path(), ignored, index, ignores, out);
            }
        }
    }

    /** Store the file's content as a blob and describe it as an index entry. This is the core of `add`. */
    public IndexEntry stage(String path, ObjectStore store) throws IOException {
        return read(path, store);
    }

    /** Like stage(), but only computes the blob id without storing anything (for status). */
    public IndexEntry describe(String path) throws IOException {
        return read(path, null);
    }

    public FileMode mode(String path) {
        Path p = resolve(path);
        if (Files.isSymbolicLink(p)) return FileMode.SYMLINK;
        return Files.isExecutable(p) ? FileMode.EXECUTABLE : FileMode.REGULAR;
    }

    public IndexEntry.Stat stat(String path) throws IOException { return stat(resolve(path)); }

    private IndexEntry read(String path, ObjectStore store) throws IOException {
        Path p = resolve(path);
        IndexEntry.Stat stat = stat(p);                                     // before reading: if the file changes
        FileMode mode = mode(path);                                         // mid-add, the stat looks stale, not fresh
        byte[] content = mode == FileMode.SYMLINK
                ? Files.readSymbolicLink(p).toString().getBytes(StandardCharsets.UTF_8)   // a link's "content" is its target
                : Files.readAllBytes(p);
        Blob blob = new Blob(content);
        return new IndexEntry(path, mode, store != null ? store.write(blob) : ObjectStore.hash(blob), stat);
    }

    /** The metadata git keeps per index entry. The "unix" view has inode, uid, etc.; elsewhere we fill in zeros. */
    static IndexEntry.Stat stat(Path p) throws IOException {
        try {
            Map<String, Object> a = Files.readAttributes(p, "unix:ctime,lastModifiedTime,dev,ino,uid,gid,size", NOFOLLOW);
            FileTime ctime = (FileTime) a.get("ctime"), mtime = (FileTime) a.get("lastModifiedTime");
            return new IndexEntry.Stat(secs(ctime), nanos(ctime), secs(mtime), nanos(mtime),
                    num(a, "dev"), num(a, "ino"), num(a, "uid"), num(a, "gid"), num(a, "size"));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {   // e.g. Windows: no unix view
            BasicFileAttributes b = Files.readAttributes(p, BasicFileAttributes.class, NOFOLLOW);
            FileTime mtime = b.lastModifiedTime();
            return new IndexEntry.Stat(0, 0, secs(mtime), nanos(mtime), 0, 0, 0, 0, (int) b.size());
        }
    }

    private static int secs(FileTime t)  { return (int) t.toInstant().getEpochSecond(); }
    private static int nanos(FileTime t) { return t.toInstant().getNano(); }
    private static int num(Map<String, Object> a, String key) { return ((Number) a.get(key)).intValue(); }   // keep low 32 bits

    private Path resolve(String path) { return path.isEmpty() ? root : root.resolve(path); }
}
