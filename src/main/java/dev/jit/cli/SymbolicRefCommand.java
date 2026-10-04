package dev.jit.cli;

import dev.jit.core.Repository;

/**
 * jit symbolic-ref <name>          print where it points, e.g. "refs/heads/main"
 * jit symbolic-ref <name> <ref>    make it point there (switching branches = HEAD -> another refs/heads/...)
 */
public final class SymbolicRefCommand implements Command {
    public String name()  { return "symbolic-ref"; }
    public String usage() { return "<name> [<ref>]  read or set a symbolic ref like HEAD"; }

    public int run(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("usage: jit symbolic-ref <name> [<ref>]");
        Repository repo = Repository.findFromCwd();
        if (args.length == 2) {
            repo.refs().setSymbolic(args[0], args[1]);
            return 0;
        }
        String target = repo.refs().readSymbolic(args[0])
                .orElseThrow(() -> new IllegalStateException("ref " + args[0] + " is not a symbolic ref"));   // e.g. detached HEAD
        System.out.println(target);
        return 0;
    }
}
