package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.TreeWriter;

/** jit write-tree: store the working directory as blobs + trees, print the root tree's id. */
public final class WriteTreeCommand implements Command {
    public String name()  { return "write-tree"; }
    public String usage() { return "snapshot the working directory as a tree object"; }

    public int run(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("usage: jit write-tree");
        Repository repo = Repository.findFromCwd();
        System.out.println(new TreeWriter(repo.objects()).write(repo.workTree()));
        return 0;
    }
}
