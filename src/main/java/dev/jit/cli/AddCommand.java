package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.WorkTree;
import dev.jit.ignore.Ignores;
import dev.jit.index.Index;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * jit add [-f] <path>...
 * Stage files for the next commit. A folder means everything inside it ("." = the whole repo).
 * A staged file that's gone from disk gets unstaged, so deletions are recorded too (like git 2.x).
 * Ignored files are skipped inside folders, and refused when named directly unless -f (force).
 */
public final class AddCommand implements Command {
    public String name()  { return "add"; }
    public String usage() { return "[-f] <path>...  stage files for the next commit"; }

    public int run(String[] args) throws Exception {
        boolean force = false;
        List<String> paths = new ArrayList<>();
        for (String a : args) {
            if (a.equals("-f") || a.equals("--force")) force = true;
            else paths.add(a);
        }
        if (paths.isEmpty()) throw new IllegalArgumentException("nothing specified, nothing added (try: jit add .)");

        Repository repo = Repository.findFromCwd();
        WorkTree work = new WorkTree(repo.workTree());
        Index index = Index.load(repo.indexFile());
        Ignores ignores = force ? Ignores.none() : Ignores.load(repo.workTree(), repo.jitDir());

        for (String arg : paths) {                                   // any error below: nothing gets saved
            String path = repo.toRepoPath(Path.of(arg));
            List<String> onDisk = work.listFiles(path, index, ignores);
            List<String> staged = index.pathsUnder(path);
            if (onDisk.isEmpty() && staged.isEmpty()) {
                if (work.isFile(path) || (!path.isEmpty() && work.exists(path) && ignores.isIgnored(path, true)))
                    throw new IllegalArgumentException("'" + arg + "' is ignored by " + Ignores.FILE_NAME + " (use -f to add it anyway)");
                if (!work.exists(path)) throw new IllegalArgumentException("pathspec '" + arg + "' did not match any files");
            }

            Set<String> present = new HashSet<>(onDisk);
            for (String p : staged) if (!present.contains(p) && !work.isFile(p)) index.remove(p);   // deleted from disk
            for (String p : onDisk) index.add(work.stage(p, repo.objects()));
        }
        index.save(repo.indexFile());
        return 0;
    }
}
