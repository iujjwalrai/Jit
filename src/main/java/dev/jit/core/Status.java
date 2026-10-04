package dev.jit.core;

import dev.jit.ignore.Ignores;
import dev.jit.index.Index;
import dev.jit.index.IndexEntry;
import dev.jit.objects.FileMode;
import dev.jit.objects.TreeEntry;
import dev.jit.storage.ObjectStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Three snapshots, two comparisons:
 *
 *   HEAD's tree  <-- staged -->  index  <-- unstaged -->  working directory
 *
 * plus untracked files: on disk, not in the index, not ignored.
 */
public final class Status {

    public enum Change {
        ADDED('A', "new file:"), MODIFIED('M', "modified:"), DELETED('D', "deleted:"), TYPECHANGE('T', "typechange:");

        public final char code;          // the letter in `status -s`
        public final String label;       // the word in long `status`

        Change(char code, String label) { this.code = code; this.label = label; }
    }

    public record Result(SortedMap<String, Change> staged, SortedMap<String, Change> unstaged, List<String> untracked) {
        public boolean clean() { return staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty(); }
    }

    public static Result compute(Repository repo) throws IOException {
        ObjectStore store = repo.objects();
        Index index = Index.load(repo.indexFile());
        WorkTree work = new WorkTree(repo.workTree());

        Map<String, TreeEntry> head = new HashMap<>();
        Optional<String> headCommit = repo.refs().read("HEAD");
        if (headCommit.isPresent()) flatten(store, store.readCommit(headCommit.get()).tree(), "", head);

        Result result = new Result(new TreeMap<>(Index.PATH_ORDER), new TreeMap<>(Index.PATH_ORDER), new ArrayList<>());

        // 1. HEAD vs index: what the next commit would change
        TreeSet<String> paths = new TreeSet<>(Index.PATH_ORDER);
        paths.addAll(head.keySet());
        for (IndexEntry e : index.entries()) paths.add(e.path());
        for (String p : paths) {
            TreeEntry h = head.get(p);
            IndexEntry i = index.get(p);
            if (h == null) result.staged.put(p, Change.ADDED);
            else if (i == null) result.staged.put(p, Change.DELETED);
            else if (!h.id().equals(i.id()) || h.mode() != i.mode()) result.staged.put(p, kind(h.mode(), i.mode()));
        }

        // 2. index vs working directory: edits not yet `add`ed
        List<IndexEntry> refreshed = new ArrayList<>();
        for (IndexEntry e : index.entries()) {
            String p = e.path();
            if (!work.isFile(p)) { result.unstaged.put(p, Change.DELETED); continue; }
            FileMode mode = work.mode(p);
            if ((mode == FileMode.SYMLINK) != (e.mode() == FileMode.SYMLINK)) { result.unstaged.put(p, Change.TYPECHANGE); continue; }
            if (mode == e.mode() && work.stat(p).equals(e.stat()) && !index.isRacy(e)) continue;   // fast path: no hashing

            IndexEntry now = work.describe(p);                               // metadata changed: compare actual content
            if (now.id().equals(e.id()) && now.mode() == e.mode()) refreshed.add(now);   // just touched, not changed
            else result.unstaged.put(p, Change.MODIFIED);
        }
        if (!refreshed.isEmpty()) {
            // remember the new stat so next time is fast again (what git calls refreshing the index)
            for (IndexEntry e : refreshed) index.add(e);
            try { index.save(repo.indexFile()); } catch (IllegalStateException lockedByOther) { /* fine: just slower next time */ }
        }

        // 3. untracked files
        untracked(work, index, Ignores.load(repo.workTree(), repo.jitDir()), "", result.untracked);
        result.untracked.sort(Index.PATH_ORDER);
        return result;
    }

    /**
     * Folders with nothing tracked inside are shown once as "dir/" rather than file by file,
     * and only if they contain at least one non-ignored file.
     */
    private static void untracked(WorkTree work, Index index, Ignores ignores, String dir, List<String> out) throws IOException {
        for (WorkTree.Child c : work.children(dir)) {
            if (!c.isDir()) {
                if (index.get(c.path()) == null && !ignores.matches(c.path(), false)) out.add(c.path());
            } else if (!ignores.matches(c.path(), true)) {
                if (!index.pathsUnder(c.path()).isEmpty()) untracked(work, index, ignores, c.path(), out);
                else if (hasUntracked(work, ignores, c.path())) out.add(c.path() + "/");
            }
        }
    }

    private static boolean hasUntracked(WorkTree work, Ignores ignores, String dir) throws IOException {
        for (WorkTree.Child c : work.children(dir)) {
            if (ignores.matches(c.path(), c.isDir())) continue;
            if (!c.isDir() || hasUntracked(work, ignores, c.path())) return true;
        }
        return false;
    }

    /** A symlink that became a regular file (or back) is a type change; anything else is a modification. */
    private static Change kind(FileMode before, FileMode after) {
        return (before == FileMode.SYMLINK) != (after == FileMode.SYMLINK) ? Change.TYPECHANGE : Change.MODIFIED;
    }

    /** Tree -> flat map of "path/to/file" -> entry, the same shape as the index. */
    static void flatten(ObjectStore store, String treeId, String prefix, Map<String, TreeEntry> out) throws IOException {
        for (TreeEntry e : store.readTree(treeId).entries()) {
            if (e.isTree()) flatten(store, e.id(), prefix + e.name() + "/", out);
            else out.put(prefix + e.name(), e);
        }
    }
}
