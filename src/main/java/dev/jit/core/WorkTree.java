package dev.jit.core;

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

    private final Path root;

    public WorkTree(Path root) { this.root = root; }

    public boolean exists(String path) { return Files.exists(resolve(path), NOFOLLOW); }

    /** Every file at or under `path` ("" = the whole tree). Folders themselves aren't listed: git only tracks files. */
    public List<String> listFiles(String path) throws IOException {
        for (String part : path.split("/"))
            if (SKIP.contains(part)) throw new IllegalArgumentException("'" + path + "' is inside repository metadata");
        List<String> out = new ArrayList<>();
        collect(resolve(path), path, out);
        return out;
    }

    private void collect(Path p, String rel, List<String> out) throws IOException {
        if (Files.isSymbolicLink(p) || Files.isRegularFile(p, NOFOLLOW)) {    // check links first: don't follow them
            out.add(rel);
            return;
        }
        if (!Files.isDirectory(p, NOFOLLOW)) return;                         // missing, or a socket/fifo: skip, like git
        List<Path> children;
        try (Stream<Path> s = Files.list(p)) { children = s.toList(); }
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (!SKIP.contains(name)) collect(child, rel.isEmpty() ? name : rel + "/" + name, out);
        }
    }

    /** Store the file's content as a blob and describe it as an index entry. This is the core of `add`. */
    public IndexEntry stage(String path, ObjectStore store) throws IOException {
        Path p = resolve(path);
        IndexEntry.Stat stat = stat(p);                                     // before reading: if the file changes
        FileMode mode;                                                      // mid-add, the stat looks stale, not fresh
        byte[] content;
        if (Files.isSymbolicLink(p)) {
            mode = FileMode.SYMLINK;
            content = Files.readSymbolicLink(p).toString().getBytes(StandardCharsets.UTF_8);   // a link's "content" is its target
        } else {
            mode = Files.isExecutable(p) ? FileMode.EXECUTABLE : FileMode.REGULAR;
            content = Files.readAllBytes(p);
        }
        return new IndexEntry(path, mode, store.write(new Blob(content)), stat);
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
