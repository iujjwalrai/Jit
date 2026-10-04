package dev.jit.core;

import dev.jit.index.Index;
import dev.jit.objects.FileMode;
import dev.jit.objects.Tree;
import dev.jit.objects.TreeEntry;
import dev.jit.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TreeWriterTest {

    @TempDir Path work;
    @TempDir Path objects;

    /** What `jit add .` does: stage every file in the working directory. */
    private Index addAll(ObjectStore store) throws Exception {
        WorkTree wt = new WorkTree(work);
        Index index = new Index();
        for (String p : wt.listFiles("")) index.add(wt.stage(p, store));
        return index;
    }

    @Test
    void snapshotMatchesGitWriteTree() throws Exception {
        // the same layout real git hashed to 98960a53...
        Files.writeString(work.resolve("hello.txt"), "hello world\n");
        Files.writeString(work.resolve("src.txt"), "f\n");
        Files.writeString(work.resolve("src-b"), "b\n");
        Files.writeString(work.resolve("run.sh"), "run\n");
        Files.setPosixFilePermissions(work.resolve("run.sh"), PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.createDirectories(work.resolve("src/a"));
        Files.writeString(work.resolve("src/a/x.txt"), "x\n");

        Files.createDirectories(work.resolve(".jit/objects"));      // repo metadata: must not be staged
        Files.createDirectories(work.resolve("empty/nested"));      // empty dirs: git doesn't record them

        ObjectStore store = new ObjectStore(objects);
        Index index = addAll(store);
        assertEquals(List.of("hello.txt", "run.sh", "src-b", "src.txt", "src/a/x.txt"),   // index order: plain bytes
                index.entries().stream().map(e -> e.path()).toList());

        String id = new TreeWriter(store).write(index);
        assertEquals("98960a53e3f1e39921b92a6bef1c880f2439f3e5", id);

        Tree root = store.readTree(id);                            // tree order: "src/" sorts after "src.txt"
        TreeEntry src = root.entries().get(4);
        assertEquals(FileMode.DIRECTORY, src.mode());
        assertEquals(FileMode.EXECUTABLE, root.entries().get(1).mode());
        assertEquals("a", store.readTree(src.id()).entries().get(0).name());
    }

    @Test
    void emptyIndexGivesEmptyTree() throws Exception {
        ObjectStore store = new ObjectStore(objects);
        String id = new TreeWriter(store).write(new Index());
        assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", id);
        assertEquals(List.of(), store.readTree(id).entries());
    }

    @Test
    void readTreeRejectsBlobs() throws Exception {
        Files.writeString(work.resolve("hello.txt"), "hello world\n");
        ObjectStore store = new ObjectStore(objects);
        new TreeWriter(store).write(addAll(store));
        assertThrows(IllegalStateException.class,
                () -> store.readTree("3b18e512dba79e4c8300dd08aeb37f8e728b8dad"));
    }

    @Test
    void symlinksAreStagedAsLinks() throws Exception {
        Files.writeString(work.resolve("target.txt"), "t\n");
        Files.createSymbolicLink(work.resolve("link"), Path.of("target.txt"));
        Index index = addAll(new ObjectStore(objects));
        assertEquals(FileMode.SYMLINK, index.get("link").mode());
        assertEquals(new ObjectStore(objects).write(new dev.jit.objects.Blob("target.txt".getBytes())), index.get("link").id());
    }

    @Test
    void cannotListInsideMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new WorkTree(work).listFiles(".jit"));
        assertThrows(IllegalArgumentException.class, () -> new WorkTree(work).listFiles("sub/.git/config"));
    }
}
