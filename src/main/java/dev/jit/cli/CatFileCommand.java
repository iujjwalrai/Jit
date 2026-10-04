package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.Revision;
import dev.jit.objects.ObjectType;
import dev.jit.objects.Tree;
import dev.jit.storage.RawObject;

/** jit cat-file (-t | -s | -p) <id> */
public final class CatFileCommand implements Command {
    public String name()  { return "cat-file"; }
    public String usage() { return "(-t|-s|-p) <rev>  show an object's type, size or content"; }

    public int run(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: jit cat-file (-t|-s|-p) <id>");
        Repository repo = Repository.findFromCwd();
        String id = Revision.resolve(repo, args[1]);                  // id, short id, HEAD, main~1, ...
        RawObject obj = repo.objects().read(id);

        switch (args[0]) {
            case "-t" -> System.out.println(obj.type().wireName());   // type:    blob
            case "-s" -> System.out.println(obj.body().length);       // size:    12
            case "-p" -> {                                            // content: hello world
                if (obj.type() == ObjectType.TREE) {                  // tree body has raw binary ids: format it like ls-tree
                    Tree.parse(obj.body()).entries().forEach(e -> System.out.println(e.format(e.name())));
                } else {
                    System.out.write(obj.body());                     // write raw bytes, not a String (binary-safe)
                    System.out.flush();
                }
            }
            default -> throw new IllegalArgumentException("unknown option " + args[0]);
        }
        return 0;
    }
}