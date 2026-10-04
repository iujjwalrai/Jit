package dev.jit.cli;

import dev.jit.objects.Signature;
import org.junit.jupiter.api.Test;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class LogCommandTest {

    @Test
    void gitDateFormat() {
        Signature s = new Signature("U", "u@example.com", 1700000300L, ZoneOffset.ofHoursMinutes(5, 30));
        assertEquals("Wed Nov 15 03:48:20 2023 +0530", LogCommand.formatDate(s));    // copied from git log
        Signature west = new Signature("U", "u@example.com", 1700000300L, ZoneOffset.ofHours(-7));
        assertEquals("Tue Nov 14 15:18:20 2023 -0700", LogCommand.formatDate(west)); // same instant, their clock
    }
}
