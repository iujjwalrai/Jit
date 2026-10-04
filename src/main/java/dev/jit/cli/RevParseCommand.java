package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.Revision;

/** jit rev-parse <rev>...   print the full id each name refers to (HEAD, main, HEAD~2, 3b18e5, ...) */
public final class RevParseCommand implements Command {
    public String name()  { return "rev-parse"; }
    public String usage() { return "<rev>...  turn names like HEAD~1 into full ids"; }

    public int run(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("usage: jit rev-parse <rev>...");
        Repository repo = Repository.findFromCwd();
        for (String rev : args) System.out.println(Revision.resolve(repo, rev));
        return 0;
    }
}
