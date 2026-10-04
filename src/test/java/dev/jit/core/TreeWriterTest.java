package dev.jit.core;

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

        Files.createDirectories(work.resolve(".jit/objects"));      // repo metadata: must not be snapshotted
        Files.createDirectories(work.resolve("empty/nested"));      // empty dirs: git doesn't record them

        ObjectStore store = new ObjectStore(objects);
        String id = new TreeWriter(store).write(work);
        assertEquals("98960a53e3f1e39921b92a6bef1c880f2439f3e5", id);

        Tree root = store.readTree(id);                            // and everything it points at was stored
        TreeEntry src = root.entries().get(4);
        assertEquals(FileMode.DIRECTORY, src.mode());
        assertEquals(FileMode.EXECUTABLE, root.entries().get(1).mode());
        assertEquals("a", store.readTree(src.id()).entries().get(0).name());
    }

    @Test
    void emptyDirectoryGivesEmptyTree() throws Exception {
        ObjectStore store = new ObjectStore(objects);
        String id = new TreeWriter(store).write(work);
        assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", id);
        assertEquals(List.of(), store.readTree(id).entries());
    }

    @Test
    void readTreeRejectsBlobs() throws Exception {
        Files.writeString(work.resolve("hello.txt"), "hello world\n");
        ObjectStore store = new ObjectStore(objects);
        new TreeWriter(store).write(work);
        assertThrows(IllegalStateException.class,
                () -> store.readTree("3b18e512dba79e4c8300dd08aeb37f8e728b8dad"));
    }
}
