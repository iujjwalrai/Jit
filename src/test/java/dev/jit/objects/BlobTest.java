package dev.jit.objects;

import dev.jit.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BlobTest {

    @Test
    void helloWorldMatchesGit() {   // printf 'hello world\n' | git hash-object --stdin
        Blob b = new Blob("hello world\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("3b18e512dba79e4c8300dd08aeb37f8e728b8dad", ObjectStore.hash(b));
    }

    @Test
    void emptyFileMatchesGit() {    // git hash-object /dev/null
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", ObjectStore.hash(new Blob(new byte[0])));
    }

    @Test
    void headerCountsBytesNotCharacters() {
        // "नमस्ते" is 6 characters but 18 bytes in UTF-8
        byte[] raw = new Blob("नमस्ते".getBytes(StandardCharsets.UTF_8)).serialize();
        assertTrue(new String(raw, StandardCharsets.UTF_8).startsWith("blob 18\0"));
    }
}