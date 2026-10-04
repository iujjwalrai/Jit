package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.storage.RawObject;

/** jit cat-file (-t | -s | -p) <id> */
public final class CatFileCommand implements Command {
    public String name()  { return "cat-file"; }
    public String usage() { return "(-t|-s|-p) <id>  show an object's type, size or content"; }

    public int run(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: jit cat-file (-t|-s|-p) <id>");
        Repository repo = Repository.findFromCwd();
        String id = repo.objects().resolvePrefix(args[1]);
        RawObject obj = repo.objects().read(id);

        switch (args[0]) {
            case "-t" -> System.out.println(obj.type().wireName());   // type:    blob
            case "-s" -> System.out.println(obj.body().length);       // size:    12
            case "-p" -> {                                            // content: hello world
                System.out.write(obj.body());                         // write raw bytes, not a String (binary-safe)
                System.out.flush();
            }
            default -> throw new IllegalArgumentException("unknown option " + args[0]);
        }
        return 0;
    }
}