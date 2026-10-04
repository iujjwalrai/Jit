package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.objects.Tree;
import dev.jit.objects.TreeEntry;
import dev.jit.storage.ObjectStore;

import java.io.IOException;

/** jit ls-tree [-r] [-t] [--name-only] <tree-id> */
public final class LsTreeCommand implements Command {
    public String name()  { return "ls-tree"; }
    public String usage() { return "[-r] [-t] [--name-only] <id>  list a tree's entries"; }

    private boolean recursive, showTrees, nameOnly;

    public int run(String[] args) throws Exception {
        String id = null;
        for (String a : args) {
            switch (a) {
                case "-r"          -> recursive = true;    // descend into subtrees
                case "-t"          -> showTrees = true;    // with -r: also print the subtrees themselves
                case "--name-only" -> nameOnly = true;
                default            -> id = a;
            }
        }
        if (id == null) throw new IllegalArgumentException("usage: jit ls-tree [-r] [-t] [--name-only] <id>");

        ObjectStore store = Repository.findFromCwd().objects();
        print(store, store.readTree(store.resolvePrefix(id)), "");
        return 0;
    }

    private void print(ObjectStore store, Tree tree, String prefix) throws IOException {
        for (TreeEntry e : tree.entries()) {
            String path = prefix + e.name();
            boolean descend = recursive && e.isTree();
            if (!descend || showTrees) System.out.println(nameOnly ? path : e.format(path));
            if (descend) print(store, store.readTree(e.id()), path + "/");
        }
    }
}
