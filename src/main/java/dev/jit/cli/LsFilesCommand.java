package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.index.Index;
import dev.jit.index.IndexEntry;

/** jit ls-files [-s]   list what's staged (-s: with mode and blob id, like git's --stage) */
public final class LsFilesCommand implements Command {
    public String name()  { return "ls-files"; }
    public String usage() { return "[-s]  list staged files"; }

    public int run(String[] args) throws Exception {
        boolean stage = args.length == 1 && args[0].equals("-s");
        if (args.length > 1 || (args.length == 1 && !stage)) throw new IllegalArgumentException("usage: jit ls-files [-s]");

        Index index = Index.load(Repository.findFromCwd().indexFile());
        StringBuilder out = new StringBuilder();
        for (IndexEntry e : index.entries()) {
            if (stage) out.append(e.mode().wireName()).append(' ').append(e.id()).append(" 0\t");   // 0 = merge stage
            out.append(e.path()).append('\n');
        }
        System.out.print(out);
        return 0;
    }
}
