package dev.jit.objects;

import dev.jit.util.Hashing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A directory listing. Body is entries back to back, no separators between them:
 *   "<mode> <name>\0<20 raw id bytes>"
 * The id is raw bytes, not hex, so a tree body is binary and can't be printed as-is.
 */
public record Tree(List<TreeEntry> entries) implements GitObject {

    public Tree {
        entries = entries.stream().sorted(TreeEntry.GIT_ORDER).toList();   // sorted copy: same contents -> same id
        Set<String> seen = new HashSet<>();
        for (TreeEntry e : entries)
            if (!seen.add(e.name())) throw new IllegalArgumentException("duplicate tree entry: " + e.name());
    }

    @Override public ObjectType type() { return ObjectType.TREE; }

    @Override public byte[] body() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TreeEntry e : entries) {
            out.writeBytes((e.mode().wireName() + " " + e.name() + "\0").getBytes(StandardCharsets.UTF_8));
            out.writeBytes(Hashing.fromHex(e.id()));                        // 20 bytes, not 40 hex chars
        }
        return out.toByteArray();
    }

    /** The reverse of body(): walk the bytes entry by entry. */
    public static Tree parse(byte[] body) {
        List<TreeEntry> entries = new ArrayList<>();
        int pos = 0;
        while (pos < body.length) {
            int space = indexOf(body, (byte) ' ', pos);                    // "100644| hello.txt\0..."
            int nul   = indexOf(body, (byte) 0, space + 1);                // "100644 hello.txt|\0..."
            if (space < 0 || nul < 0 || nul + 21 > body.length)
                throw new IllegalStateException("corrupt tree at byte " + pos);

            FileMode mode = FileMode.fromWireName(new String(body, pos, space - pos, StandardCharsets.US_ASCII));
            String name = new String(body, space + 1, nul - space - 1, StandardCharsets.UTF_8);
            byte[] rawId = new byte[20];
            System.arraycopy(body, nul + 1, rawId, 0, 20);
            entries.add(new TreeEntry(mode, name, Hashing.toHex(rawId)));
            pos = nul + 21;                                                 // skip the NUL and the 20 id bytes
        }
        return new Tree(entries);
    }

    private static int indexOf(byte[] data, byte b, int from) {
        if (from < 0) return -1;
        for (int i = from; i < data.length; i++) if (data[i] == b) return i;
        return -1;
    }
}
