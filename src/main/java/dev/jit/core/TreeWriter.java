package dev.jit.core;

import dev.jit.objects.Blob;
import dev.jit.objects.FileMode;
import dev.jit.objects.Tree;
import dev.jit.objects.TreeEntry;
import dev.jit.storage.ObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Snapshot a directory into the object store: every file becomes a blob, every folder a tree.
 * Works bottom-up, because a tree needs its children's ids before it can be hashed itself.
 * (Real git builds trees from the index instead; we have no index yet, so we read the disk directly.)
 */
public final class TreeWriter {
    private static final Set<String> SKIP = Set.of(Repository.DIR_NAME, ".git");   // never snapshot repo metadata

    private final ObjectStore store;

    public TreeWriter(ObjectStore store) { this.store = store; }

    /** Write the whole directory and return the root tree's id. An empty directory gives the empty tree. */
    public String write(Path dir) throws IOException {
        String id = writeDir(dir);
        return id != null ? id : store.write(new Tree(List.of()));     // 4b825dc6... the empty tree
    }

    /** Returns null for a folder with nothing in it: git doesn't record empty directories. */
    private String writeDir(Path dir) throws IOException {
        List<Path> children;
        try (Stream<Path> s = Files.list(dir)) { children = s.toList(); }

        List<TreeEntry> entries = new ArrayList<>();
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (SKIP.contains(name)) continue;

            if (Files.isSymbolicLink(child)) {                         // check first: isDirectory() would follow the link
                byte[] target = Files.readSymbolicLink(child).toString().getBytes(StandardCharsets.UTF_8);
                entries.add(new TreeEntry(FileMode.SYMLINK, name, store.write(new Blob(target))));
            } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                String subtree = writeDir(child);                      // recurse: children first
                if (subtree != null) entries.add(new TreeEntry(FileMode.DIRECTORY, name, subtree));
            } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                FileMode mode = Files.isExecutable(child) ? FileMode.EXECUTABLE : FileMode.REGULAR;
                entries.add(new TreeEntry(mode, name, store.write(new Blob(Files.readAllBytes(child)))));
            }                                                          // anything else (sockets, fifos): skip, like git
        }
        return entries.isEmpty() ? null : store.write(new Tree(entries));
    }
}
