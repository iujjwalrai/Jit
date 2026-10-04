package dev.jit.core;

import dev.jit.index.Index;
import dev.jit.index.IndexEntry;
import dev.jit.objects.FileMode;
import dev.jit.objects.Tree;
import dev.jit.objects.TreeEntry;
import dev.jit.storage.ObjectStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turn the index's flat list of paths into nested tree objects, like git write-tree:
 *   a.txt, src/Main.java, src/util/X.java   ->   root tree { a.txt, src/ -> tree { Main.java, util/ -> tree { X.java } } }
 * Works bottom-up, because a tree needs its children's ids before it can be hashed itself.
 * The blobs are already stored (`add` did that), so only trees get written here.
 */
public final class TreeWriter {
    private final ObjectStore store;

    public TreeWriter(ObjectStore store) { this.store = store; }

    /** Returns the root tree's id. An empty index gives the empty tree (4b825dc6...). */
    public String write(Index index) throws IOException {
        return writeDir(List.copyOf(index.entries()), "");
    }

    /**
     * `entries` all start with `prefix` and are sorted by path, so everything inside one subfolder
     * sits next to each other: "src/a", "src/b" can't have "srcx" between them.
     */
    private String writeDir(List<IndexEntry> entries, String prefix) throws IOException {
        List<TreeEntry> out = new ArrayList<>();
        int i = 0;
        while (i < entries.size()) {
            IndexEntry e = entries.get(i);
            String rest = e.path().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash < 0) {                                                // a file directly in this folder
                out.add(new TreeEntry(e.mode(), rest, e.id()));
                i++;
                continue;
            }
            String dir = rest.substring(0, slash);                          // a subfolder: take all its entries at once
            String dirPrefix = prefix + dir + "/";
            int j = i;
            while (j < entries.size() && entries.get(j).path().startsWith(dirPrefix)) j++;
            out.add(new TreeEntry(FileMode.DIRECTORY, dir, writeDir(entries.subList(i, j), dirPrefix)));
            i = j;
        }
        return store.write(new Tree(out));
    }
}
