package dev.jit.core;

import dev.jit.objects.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IdentityTest {

    @TempDir Path tmp;

    private Config config(String text) throws Exception {
        Path file = tmp.resolve("config");
        Files.writeString(file, text);
        return Config.load(file);
    }

    @Test
    void configParsesGitFormat() throws Exception {
        Config c = config("""
                # comment
                [core]
                \tbare = false
                [User]
                \tName = "Ujjwal Rai"
                \temail = ujjwal@example.com
                [branch "Main"]
                \tremote = origin
                """);
        assertEquals("false", c.get("core.bare").orElseThrow());
        assertEquals("Ujjwal Rai", c.get("user.name").orElseThrow());     // section/key case-insensitive, quotes stripped
        assertEquals("ujjwal@example.com", c.get("USER.EMAIL").orElseThrow());
        assertEquals("origin", c.get("branch.Main.remote").orElseThrow());  // subsection keeps its case
        assertTrue(c.get("branch.main.remote").isEmpty());
        assertTrue(c.get("user.missing").isEmpty());
    }

    @Test
    void missingConfigFileIsEmpty() throws Exception {
        assertTrue(Config.load(tmp.resolve("nope")).get("user.name").isEmpty());
    }

    @Test
    void configIsUsedWhenNoEnv() throws Exception {
        Config c = config("[user]\n\tname = From Config\n\temail = cfg@example.com\n");
        Instant now = Instant.ofEpochSecond(1700000000L);
        Signature s = Identity.resolve("AUTHOR", c, Map.of(), now);
        assertEquals("From Config", s.name());
        assertEquals("cfg@example.com", s.email());
        assertEquals(1700000000L, s.epochSeconds());
    }

    @Test
    void envBeatsConfig() throws Exception {
        Config c = config("[user]\n\tname = From Config\n\temail = cfg@example.com\n");
        Signature s = Identity.resolve("COMMITTER", c, Map.of(
                "JIT_COMMITTER_NAME", "From Env",
                "JIT_COMMITTER_DATE", "1700000000 -0700"), Instant.now());
        assertEquals("From Env", s.name());
        assertEquals("cfg@example.com", s.email());                     // only the name was overridden
        assertEquals(ZoneOffset.ofHours(-7), s.offset());
        assertEquals(1700000000L, s.epochSeconds());
    }

    @Test
    void noIdentityIsAnError() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> Identity.resolve("AUTHOR", config(""), Map.of(), Instant.now()));
    }
}
