package dev.jit.storage;

import dev.jit.objects.Blob;
import dev.jit.objects.ObjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ObjectStoreTest {

    @TempDir Path tmp;   // JUnit creates a fresh empty folder for each test and deletes it afterwards

    private static Blob blob(String s) { return new Blob(s.getBytes(StandardCharsets.UTF_8)); }

    @Test
    void writeThenReadRoundTrips() throws Exception {
        ObjectStore store = new ObjectStore(tmp);
        String id = store.write(blob("hello world\n"));

        assertTrue(Files.exists(tmp.resolve("3b/18e512dba79e4c8300dd08aeb37f8e728b8dad")));
        RawObject obj = store.read(id);
        assertEquals(ObjectType.BLOB, obj.type());
        assertEquals("hello world\n", new String(obj.body(), StandardCharsets.UTF_8));
    }

    @Test
    void sameContentStoredOnce() throws Exception {
        ObjectStore store = new ObjectStore(tmp);
        assertEquals(store.write(blob("same")), store.write(blob("same")));
    }

    @Test
    void resolvesShortIds() throws Exception {
        ObjectStore store = new ObjectStore(tmp);
        String id = store.write(blob("hello world\n"));
        assertEquals(id, store.resolvePrefix("3b18e5"));
        assertThrows(IllegalStateException.class, () -> store.resolvePrefix("3b1"));    // too short
        assertThrows(IllegalStateException.class, () -> store.resolvePrefix("ffff"));   // doesn't exist
    }
}