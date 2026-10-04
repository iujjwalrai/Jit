package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.TreeWriter;
import dev.jit.index.Index;

/** jit write-tree: turn the index (what's staged) into tree objects, print the root tree's id. */
public final class WriteTreeCommand implements Command {
    public String name()  { return "write-tree"; }
    public String usage() { return "write the staged files as a tree object"; }

    public int run(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("usage: jit write-tree");
        Repository repo = Repository.findFromCwd();
        System.out.println(new TreeWriter(repo.objects()).write(Index.load(repo.indexFile())));
        return 0;
    }
}
