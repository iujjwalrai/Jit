package dev.jit.objects;

import dev.jit.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TreeTest {

    private static final String HELLO = "3b18e512dba79e4c8300dd08aeb37f8e728b8dad";   // blob "hello world\n"

    @Test
    void emptyTreeMatchesGit() {    // printf '' | git mktree
        assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", ObjectStore.hash(new Tree(List.of())));
    }

    @Test
    void singleFileMatchesGit() {   // hello.txt = "hello world\n"; git add -A && git write-tree
        Tree t = new Tree(List.of(new TreeEntry(FileMode.REGULAR, "hello.txt", HELLO)));
        assertEquals("68aba62e560c0ebc3396e8ae9335232cd93a3f60", ObjectStore.hash(t));
    }

    @Test
    void directoriesSortAsIfTheyEndInSlash() {
        // given in the "wrong" order on purpose; git's order is src-b, src.txt, src/
        Tree t = new Tree(List.of(
                new TreeEntry(FileMode.DIRECTORY,  "src",       "51f8735404aabfce811e5771917e71e22f937b46"),
                new TreeEntry(FileMode.REGULAR,    "src.txt",   "6a69f92020f5df77af6e8813ff1232493383b708"),
                new TreeEntry(FileMode.EXECUTABLE, "run.sh",    "f5bdd214e01603ecd6c83be9f66d88579c588ec6"),
                new TreeEntry(FileMode.REGULAR,    "src-b",     "61780798228d17af2d34fce4cfbdf35556832472"),
                new TreeEntry(FileMode.REGULAR,    "hello.txt", HELLO)));
        assertEquals(List.of("hello.txt", "run.sh", "src-b", "src.txt", "src"),
                t.entries().stream().map(TreeEntry::name).toList());
        assertEquals("98960a53e3f1e39921b92a6bef1c880f2439f3e5", ObjectStore.hash(t));   // same id git gives
    }

    @Test
    void parseRoundTrips() {
        Tree t = new Tree(List.of(
                new TreeEntry(FileMode.REGULAR,   "hello.txt", HELLO),
                new TreeEntry(FileMode.DIRECTORY, "src",       "51f8735404aabfce811e5771917e71e22f937b46"),
                new TreeEntry(FileMode.SYMLINK,   "link",      HELLO)));
        assertEquals(t, Tree.parse(t.body()));
    }

    @Test
    void rejectsBadEntries() {
        assertThrows(IllegalArgumentException.class, () -> new TreeEntry(FileMode.REGULAR, "a/b", HELLO));
        assertThrows(IllegalArgumentException.class, () -> new TreeEntry(FileMode.REGULAR, "", HELLO));
        assertThrows(IllegalArgumentException.class, () -> new Tree(List.of(
                new TreeEntry(FileMode.REGULAR, "x", HELLO),
                new TreeEntry(FileMode.REGULAR, "x", HELLO))));
    }

    @Test
    void directoryModeIsPaddedOnlyForDisplay() {
        assertEquals("40000", FileMode.DIRECTORY.wireName());
        assertEquals("040000", FileMode.DIRECTORY.displayName());
    }
}
