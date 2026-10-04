package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.WorkTree;
import dev.jit.index.Index;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * jit add <path>...
 * Stage files for the next commit. A folder means everything inside it ("." = the whole repo).
 * A staged file that's gone from disk gets unstaged, so deletions are recorded too (like git 2.x).
 */
public final class AddCommand implements Command {
    public String name()  { return "add"; }
    public String usage() { return "<path>...  stage files for the next commit"; }

    public int run(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("nothing specified, nothing added (try: jit add .)");
        Repository repo = Repository.findFromCwd();
        WorkTree work = new WorkTree(repo.workTree());
        Index index = Index.load(repo.indexFile());

        for (String arg : args) {                                    // any error below: nothing gets saved
            String path = repo.toRepoPath(Path.of(arg));
            List<String> onDisk = work.listFiles(path);
            List<String> staged = index.pathsUnder(path);
            if (onDisk.isEmpty() && staged.isEmpty() && !work.exists(path))
                throw new IllegalArgumentException("pathspec '" + arg + "' did not match any files");

            Set<String> present = new HashSet<>(onDisk);
            for (String p : staged) if (!present.contains(p)) index.remove(p);   // deleted from disk
            for (String p : onDisk) index.add(work.stage(p, repo.objects()));
        }
        index.save(repo.indexFile());
        return 0;
    }
}
