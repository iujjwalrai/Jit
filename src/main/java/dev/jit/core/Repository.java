package dev.jit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Repository {
    public static final String DIR_NAME = ".jit";

    private final Path workTree;
    private final Path jitDir;

    private Repository(Path workTree) {
        this.workTree = workTree.toAbsolutePath().normalize();
        this.jitDir = this.workTree.resolve(DIR_NAME);
    }

    public static Repository init(Path where) throws IOException {
        Repository repo = new Repository(where);
        Files.createDirectories(repo.jitDir.resolve("objects"));
        Files.createDirectories(repo.jitDir.resolve("refs/heads"));
        Files.createDirectories(repo.jitDir.resolve("refs/tags"));
        writeIfAbsent(repo.jitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        writeIfAbsent(repo.jitDir.resolve("config"),
                "[core]\n\trepositoryformatversion = 0\n\tfilemode = false\n\tbare = false\n");
        return repo;
    }

    /** Walk up from start until a folder containing .jit is found. This is how git finds its repo. */
    public static Repository find(Path start) {
        Path cur = start.toAbsolutePath().normalize();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve(DIR_NAME))) return new Repository(cur);
            cur = cur.getParent();
        }
        throw new IllegalStateException("not a jit repository (or any parent): " + DIR_NAME);
    }

    public Path workTree() { return workTree; }
    public Path jitDir()   { return jitDir; }

    private static void writeIfAbsent(Path p, String content) throws IOException {
        if (!Files.exists(p)) Files.writeString(p, content);
    }
}