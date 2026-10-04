package dev.jit.cli;

import dev.jit.core.Repository;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InitCommand implements Command {
    public String name()  { return "init"; }
    public String usage() { return "[dir]  create an empty repository"; }

    public int run(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : ".");
        boolean existed = Files.isDirectory(dir.resolve(Repository.DIR_NAME));
        Repository repo = Repository.init(dir);
        System.out.println((existed ? "Reinitialized existing" : "Initialized empty")
                + " jit repository in " + repo.jitDir());
        return 0;
    }
}