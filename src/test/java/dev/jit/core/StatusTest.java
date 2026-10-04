package dev.jit.core;

import dev.jit.core.Status.Change;
import dev.jit.index.Index;
import dev.jit.index.IndexEntry;
import dev.jit.objects.Commit;
import dev.jit.objects.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StatusTest {

    private static final Signature ME = new Signature("U", "u@example.com", 1700000000L, ZoneOffset.UTC);

    @TempDir Path dir;
    Repository repo;
    WorkTree work;

    @BeforeEach
    void setUp() throws Exception {
        repo = Repository.init(dir);
        work = new WorkTree(dir);
    }

    private void write(String path, String content) throws Exception {
        Path p = dir.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private void add(String... paths) throws Exception {
        Index index = Index.load(repo.indexFile());
        for (String p : paths) index.add(work.stage(p, repo.objects()));
        index.save(repo.indexFile());
    }

    private void commit() throws Exception {
        String tree = new TreeWriter(repo.objects()).write(Index.load(repo.indexFile()));
        List<String> parents = repo.refs().read("HEAD").stream().toList();
        repo.refs().update("HEAD", repo.objects().write(new Commit(tree, parents, ME, ME, "c\n")), null);
    }

    @Test
    void allThreeKinds() throws Exception {
        write("kept.txt", "same\n");
        write("edited.txt", "v1\n");
        write("removed.txt", "bye\n");
        add("kept.txt", "edited.txt", "removed.txt");
        commit();

        write("edited.txt", "v2, longer\n");                       // unstaged modification
        Files.delete(dir.resolve("removed.txt"));                   // unstaged deletion
        write("new.txt", "hi\n");
        add("new.txt");                                             // staged addition
        write("new.txt", "hi again\n");                             // ...then edited again: both columns
        write("untracked/deep/file.txt", "u\n");                    // untracked folder: shown once

        Status.Result r = Status.compute(repo);
        assertEquals(Map.of("new.txt", Change.ADDED), r.staged());
        assertEquals(Map.of("edited.txt", Change.MODIFIED, "removed.txt", Change.DELETED, "new.txt", Change.MODIFIED), r.unstaged());
        assertEquals(List.of("untracked/"), r.untracked());
    }

    @Test
    void cleanAfterCommit() throws Exception {
        write("a.txt", "a\n");
        add("a.txt");
        commit();
        assertTrue(Status.compute(repo).clean());
    }

    @Test
    void touchedButUnchangedIsCleanAndGetsRefreshed() throws Exception {
        write("a.txt", "a\n");
        add("a.txt");
        commit();
        Files.setLastModifiedTime(dir.resolve("a.txt"), FileTime.from(Instant.now().plusSeconds(60)));   // like `touch`

        assertTrue(Status.compute(repo).clean());                  // stat differs, so it hashed: same content
        IndexEntry refreshed = Index.load(repo.indexFile()).get("a.txt");
        assertEquals(work.stat("a.txt"), refreshed.stat());         // and saved the new stat for next time
    }

    @Test
    void sameSizeSameSecondEditIsStillCaught() throws Exception {
        write("a.txt", "aaaa\n");
        add("a.txt");
        commit();
        IndexEntry.Stat before = Index.load(repo.indexFile()).get("a.txt").stat();
        write("a.txt", "bbbb\n");                                   // same size, probably same timestamp
        Files.setLastModifiedTime(dir.resolve("a.txt"),
                FileTime.from(Instant.ofEpochSecond(Integer.toUnsignedLong(before.mtimeSec()), before.mtimeNsec())));
        assertEquals(Map.of("a.txt", Change.MODIFIED), Status.compute(repo).unstaged());   // racy entry: hashed anyway
    }

    @Test
    void stagedDeletionAndTypeChange() throws Exception {
        write("a.txt", "a\n");
        write("target.txt", "t\n");
        add("a.txt", "target.txt");
        commit();
        Index index = Index.load(repo.indexFile());
        index.remove("a.txt");
        index.save(repo.indexFile());
        Files.delete(dir.resolve("target.txt"));
        Files.createSymbolicLink(dir.resolve("target.txt"), Path.of("a.txt"));

        Status.Result r = Status.compute(repo);
        assertEquals(Map.of("a.txt", Change.DELETED), r.staged());
        assertEquals(Map.of("target.txt", Change.TYPECHANGE), r.unstaged());
        assertEquals(List.of("a.txt"), r.untracked());              // still on disk, no longer staged
    }

    @Test
    void ignoredFilesAreNotUntracked() throws Exception {
        write(".jitignore", "*.log\nbuild/\n");
        write("app.log", "x\n");
        write("build/out.o", "x\n");
        write("only-ignored/x.log", "x\n");                         // folder with nothing but ignored files
        write("src/Main.java", "x\n");
        assertEquals(List.of(".jitignore", "src/"), Status.compute(repo).untracked());
    }
}
