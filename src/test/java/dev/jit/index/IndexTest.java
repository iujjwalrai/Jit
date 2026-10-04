package dev.jit.index;

import dev.jit.objects.FileMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static final String HELLO = "3b18e512dba79e4c8300dd08aeb37f8e728b8dad";

    @TempDir Path tmp;

    private static IndexEntry entry(String path) {
        return new IndexEntry(path, FileMode.REGULAR, HELLO,
                new IndexEntry.Stat(1700000000, 123, 1700000001, 456, 16777230, -5, 501, 20, 12));   // ino past 2^31 wraps negative
    }

    private static List<String> paths(Index index) { return index.entries().stream().map(IndexEntry::path).toList(); }

    @Test
    void saveAndLoadRoundTrips() throws Exception {
        Index index = new Index();
        index.add(entry("b.txt"));
        index.add(new IndexEntry("bin/run", FileMode.EXECUTABLE, HELLO, IndexEntry.Stat.ZERO));
        index.add(entry("नमस्ते.txt"));
        Path file = tmp.resolve("index");
        index.save(file);

        Index loaded = Index.load(file);
        assertEquals(List.copyOf(index.entries()), List.copyOf(loaded.entries()));
        assertFalse(Files.exists(tmp.resolve("index.lock")));
    }

    @Test
    void headerAndPaddingFollowGitFormat() {
        for (int len = 1; len <= 17; len++) {                       // every padding size from 1 to 8 NULs
            Index index = new Index();
            index.add(entry("x".repeat(len)));
            byte[] bytes = index.serialize();
            int entryLen = bytes.length - 12 - 20;                   // minus header and checksum
            assertEquals(0, entryLen % 8, "len " + len);
            assertTrue(entryLen >= 62 + len + 1, "at least one NUL, len " + len);
            assertTrue(entryLen <= 62 + len + 8, "at most 8 NULs, len " + len);
        }
        ByteBuffer b = ByteBuffer.wrap(new Index().serialize());
        assertEquals('D', b.get()); assertEquals('I', b.get()); assertEquals('R', b.get()); assertEquals('C', b.get());
        assertEquals(2, b.getInt());                                 // version
        assertEquals(0, b.getInt());                                 // entry count
    }

    @Test
    void sortedByRawBytesNotTreeOrder() {
        Index index = new Index();
        for (String p : List.of("src/a", "src.txt", "src-b", "a")) index.add(entry(p));
        assertEquals(List.of("a", "src-b", "src.txt", "src/a"), paths(index));   // '-' < '.' < '/'
    }

    @Test
    void fileAndFolderWithSameNameReplaceEachOther() {
        Index index = new Index();
        index.add(entry("docs/a.txt"));
        index.add(entry("docs/sub/b.txt"));
        index.add(entry("docs-x"));
        index.add(entry("docs"));                                    // folder became a file
        assertEquals(List.of("docs", "docs-x"), paths(index));
        index.add(entry("docs/c.txt"));                              // and back to a folder
        assertEquals(List.of("docs-x", "docs/c.txt"), paths(index));
    }

    @Test
    void pathsUnderAFolder() {
        Index index = new Index();
        for (String p : List.of("a", "src/x", "src/y/z", "src-b", "srcx")) index.add(entry(p));
        assertEquals(List.of("src/x", "src/y/z"), index.pathsUnder("src"));
        assertEquals(List.of("a"), index.pathsUnder("a"));
        assertEquals(5, index.pathsUnder("").size());
        assertEquals(List.of(), index.pathsUnder("nope"));
    }

    @Test
    void corruptionIsDetected() throws Exception {
        Index index = new Index();
        index.add(entry("a.txt"));
        Path file = tmp.resolve("index");
        index.save(file);
        byte[] bytes = Files.readAllBytes(file);
        bytes[20] ^= 1;                                              // flip one bit
        Files.write(file, bytes);
        assertThrows(IllegalStateException.class, () -> Index.load(file));
    }

    @Test
    void missingFileIsEmptyIndex() throws Exception {
        assertEquals(0, Index.load(tmp.resolve("nope")).size());
    }

    @Test
    void rejectsBadPaths() {
        for (String bad : List.of("", "/abs", "dir/", "a//b", "../x", "a/./b", ".jit/HEAD", "sub/.git/config"))
            assertThrows(IllegalArgumentException.class, () -> entry(bad), bad);
    }
}
