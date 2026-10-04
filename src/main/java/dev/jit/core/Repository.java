package dev.jit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.jit.storage.ObjectStore;
import dev.jit.storage.Refs;
public final class Repository {
    public static final String DIR_NAME = ".jit";

    private final Path workTree;
    private final Path jitDir;
    private final ObjectStore objects;
    private final Refs refs;

    private Repository(Path workTree) {
        this.workTree = workTree.toAbsolutePath().normalize();
        this.jitDir = this.workTree.resolve(DIR_NAME);
        this.objects = new ObjectStore(jitDir.resolve("objects"));
        this.refs = new Refs(jitDir);
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
    public static Repository findFromCwd() {                         // new: find the repo from wherever you are
        return find(Path.of(""));                                    // "" = the current directory
    }

    public Path workTree() { return workTree; }
    public Path jitDir()   { return jitDir; }
    public ObjectStore objects() { return objects; }
    public Refs refs()           { return refs; }
    public Path indexFile()      { return jitDir.resolve("index"); }

    /** A path the user typed (relative to where they are) -> repo-relative with "/" separators; "" = the root. */
    public String toRepoPath(Path userPath) {
        Path abs = userPath.toAbsolutePath().normalize();
        if (!abs.startsWith(workTree)) throw new IllegalArgumentException(userPath + " is outside the repository at " + workTree);
        return workTree.relativize(abs).toString().replace('\\', '/');
    }

    /** The reverse, for output: a repo path as seen from the current directory, e.g. "../README.md". */
    public String displayPath(String repoPath) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        String rel = cwd.relativize(workTree.resolve(repoPath)).toString().replace('\\', '/');
        return repoPath.endsWith("/") && !rel.endsWith("/") ? rel + "/" : rel;   // keep the "dir/" marker
    }
    public Config config() throws IOException { return Config.load(jitDir.resolve("config")); }   // re-read each time: user may edit it

    private static void writeIfAbsent(Path p, String content) throws IOException {
        if (!Files.exists(p)) Files.writeString(p, content);
    }
}