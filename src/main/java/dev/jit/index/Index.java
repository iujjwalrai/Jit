package dev.jit.index;

import dev.jit.objects.FileMode;
import dev.jit.util.Hashing;
import dev.jit.util.LockFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The staging area (.jit/index): the list of files the next commit will contain.
 * Same binary format as git's (version 2), so real git can read it too:
 *
 *   header   "DIRC" | version (4 bytes) | entry count (4 bytes)
 *   entries  sorted by path, each:
 *              10 x 32-bit numbers: ctime s, ctime ns, mtime s, mtime ns, dev, ino, mode, uid, gid, size
 *              20 bytes raw object id
 *              16-bit flags (low 12 bits = path length)
 *              path, then 1-8 NUL bytes so the entry's length is a multiple of 8
 *   extensions (optional caches git may add; we skip them when reading)
 *   trailer  SHA-1 of everything above, to detect a corrupt or half-written file
 */
public final class Index {
    private static final byte[] SIGNATURE = "DIRC".getBytes(StandardCharsets.US_ASCII);
    private static final int ENTRY_HEADER = 62;              // 10*4 stat bytes + 20 id bytes + 2 flag bytes
    private static final int NAME_MASK = 0xFFF;
    private static final int EXTENDED_FLAG = 0x4000;         // version 3: two more flag bytes follow

    // path -> entry, in git's order: compare paths as raw UTF-8 bytes
    private final TreeMap<String, IndexEntry> entries = new TreeMap<>(
            (a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));

    public static Index load(Path file) throws IOException {
        Index index = new Index();
        if (!Files.exists(file)) return index;               // nothing staged yet: same as an empty index
        index.parse(Files.readAllBytes(file));
        return index;
    }

    public Collection<IndexEntry> entries() { return entries.values(); }
    public IndexEntry get(String path)       { return entries.get(path); }
    public int size()                        { return entries.size(); }

    /**
     * Stage a file, replacing any older version of it. A path can't be both a file and a folder,
     * so adding "docs" removes "docs/..." entries, and adding "docs/a.txt" removes a file "docs".
     */
    public void add(IndexEntry e) {
        removeUnder(e.path());
        String[] parts = e.path().split("/");
        StringBuilder parent = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) parent.append('/');
            entries.remove(parent.append(parts[i]).toString());
        }
        entries.put(e.path(), e);
    }

    public void remove(String path) { entries.remove(path); }

    /** All staged paths at or under a path ("" = everything). */
    public List<String> pathsUnder(String path) {
        if (path.isEmpty()) return List.copyOf(entries.keySet());
        List<String> out = new ArrayList<>();
        if (entries.containsKey(path)) out.add(path);
        out.addAll(childrenOf(path).keySet());
        return out;
    }

    private void removeUnder(String dir) { childrenOf(dir).clear(); }   // clearing a subMap view removes from the map

    /** Entries inside folder `dir`: everything from "dir/" up to (not including) "dir0", as '0' is the byte after '/'. */
    private SortedMap<String, IndexEntry> childrenOf(String dir) {
        return entries.subMap(dir + "/", dir + "0");
    }

    /** Write via a lock file, so a crash mid-write never leaves a broken index behind. */
    public void save(Path file) throws IOException {
        try (LockFile lock = LockFile.acquire(file)) {
            lock.write(serialize());
            lock.commit();
        }
    }

    byte[] serialize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(12);         // ByteBuffer writes big-endian, as git wants
        header.put(SIGNATURE).putInt(2).putInt(entries.size());
        out.writeBytes(header.array());

        for (IndexEntry e : entries.values()) {
            byte[] path = e.path().getBytes(StandardCharsets.UTF_8);
            int padded = (ENTRY_HEADER + path.length + 8) & ~7;   // room for at least one NUL, rounded up to 8
            ByteBuffer b = ByteBuffer.allocate(padded);
            IndexEntry.Stat s = e.stat();
            b.putInt(s.ctimeSec()).putInt(s.ctimeNsec()).putInt(s.mtimeSec()).putInt(s.mtimeNsec())
             .putInt(s.dev()).putInt(s.ino())
             .putInt(Integer.parseInt(e.mode().wireName(), 8))      // "100644" is octal: type bits + permissions
             .putInt(s.uid()).putInt(s.gid()).putInt(s.size());
            b.put(Hashing.fromHex(e.id()));
            b.putShort((short) Math.min(path.length, NAME_MASK));  // stage 0, no flags: just the length
            b.put(path);                                            // rest of the buffer is already zeros
            out.writeBytes(b.array());
        }
        byte[] body = out.toByteArray();
        out.writeBytes(Hashing.sha1(body));
        return out.toByteArray();
    }

    private void parse(byte[] data) {
        if (data.length < 12 + 20) throw new IllegalStateException("index file is too short");
        byte[] expected = Hashing.sha1(Arrays.copyOfRange(data, 0, data.length - 20));
        if (!Arrays.equals(expected, Arrays.copyOfRange(data, data.length - 20, data.length)))
            throw new IllegalStateException("index file is corrupt (checksum mismatch)");

        ByteBuffer b = ByteBuffer.wrap(data, 0, data.length - 20);
        byte[] sig = new byte[4];
        b.get(sig);
        if (!Arrays.equals(sig, SIGNATURE)) throw new IllegalStateException("not an index file");
        int version = b.getInt();
        if (version != 2 && version != 3)                     // v4 compresses paths; we don't support it
            throw new IllegalStateException("unsupported index version " + version);
        int count = b.getInt();

        for (int i = 0; i < count; i++) {
            int start = b.position();
            int ctimeSec = b.getInt(), ctimeNsec = b.getInt(), mtimeSec = b.getInt(), mtimeNsec = b.getInt();
            int dev = b.getInt(), ino = b.getInt(), mode = b.getInt(), uid = b.getInt(), gid = b.getInt(), size = b.getInt();
            IndexEntry.Stat stat = new IndexEntry.Stat(ctimeSec, ctimeNsec, mtimeSec, mtimeNsec, dev, ino, uid, gid, size);
            byte[] id = new byte[20];
            b.get(id);
            int flags = Short.toUnsignedInt(b.getShort());
            if ((flags & 0x3000) != 0)
                throw new IllegalStateException("index has unresolved merge conflicts; not supported yet");
            if ((flags & EXTENDED_FLAG) != 0) b.getShort();   // v3 extra flags (intent-to-add etc.): ignored

            int nameStart = b.position(), nul = nameStart;
            while (data[nul] != 0) nul++;                      // path ends at the first NUL
            String path = new String(data, nameStart, nul - nameStart, StandardCharsets.UTF_8);
            int headerLen = nameStart - start;
            b.position(start + ((headerLen + (nul - nameStart) + 8) & ~7));   // skip the padding

            entries.put(path, new IndexEntry(path, FileMode.fromWireName(Integer.toOctalString(mode)),
                    Hashing.toHex(id), stat));
        }
        // extensions: 4-byte name + 4-byte size + data. Uppercase first letter = optional cache, safe to skip.
        while (b.remaining() >= 8) {
            byte first = data[b.position()];
            if (first < 'A' || first > 'Z')
                throw new IllegalStateException("index uses a required extension jit doesn't understand");
            b.position(b.position() + 4);
            int size = b.getInt();
            b.position(b.position() + size);
        }
    }
}
