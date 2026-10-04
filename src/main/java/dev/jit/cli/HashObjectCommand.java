package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.objects.Blob;
import dev.jit.storage.ObjectStore;

import java.nio.file.Files;
import java.nio.file.Path;

/** jit hash-object [-w] <file>   or   jit hash-object [-w] --stdin */
public final class HashObjectCommand implements Command {
    public String name()  { return "hash-object"; }
    public String usage() { return "[-w] <file>|--stdin  print a file's blob id (-w also stores it)"; }

    public int run(String[] args) throws Exception {
        boolean write = false, stdin = false;
        String file = null;
        for (String a : args) {
            switch (a) {
                case "-w"      -> write = true;
                case "--stdin" -> stdin = true;
                default        -> file = a;
            }
        }
        if (!stdin && file == null) throw new IllegalArgumentException("usage: jit hash-object [-w] <file>|--stdin");

        byte[] data = stdin ? System.in.readAllBytes() : Files.readAllBytes(Path.of(file));
        Blob blob = new Blob(data);
        String id = write
                ? Repository.findFromCwd().objects().write(blob)   // -w: needs a repo, stores the object
                : ObjectStore.hash(blob);                          // no -w: just compute; works anywhere
        System.out.println(id);
        return 0;
    }
}