package dev.jit.storage;

import dev.jit.objects.GitObject;
import dev.jit.objects.ObjectType;
import dev.jit.util.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class ObjectStore {

    private final Path objectsDir;                     // .jit/objects

    public ObjectStore(Path objectsDir) { this.objectsDir = objectsDir; }

    /** Compute the id without storing anything (hash-object without -w). */
    public static String hash(GitObject obj) {
        return Hashing.toHex(Hashing.sha1(obj.serialize()));
    }

    /** Store the object; return its 40-char id. */
    public String write(GitObject obj) throws IOException {
        byte[] raw = obj.serialize();
        String id = Hashing.toHex(Hashing.sha1(raw));
        Path target = pathFor(id);
        if (Files.exists(target)) return id;           // already stored: same content = same id, nothing to do

        Files.createDirectories(target.getParent());   // .jit/objects/3b/
        Path tmp = Files.createTempFile(target.getParent(), "tmp_", null);
        try (OutputStream out = new DeflaterOutputStream(Files.newOutputStream(tmp))) {
            out.write(raw);                            // DeflaterOutputStream = zlib compression, the format git uses
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(tmp);                 // written by someone else meanwhile; identical bytes, so fine
        }
        return id;
    }

    /** Read an object back: decompress, check the header, split off the body. */
    public RawObject read(String id) throws IOException {
        Path p = pathFor(id);
        if (!Files.exists(p)) throw new IllegalStateException("object not found: " + id);

        byte[] raw;
        try (InputStream in = new InflaterInputStream(Files.newInputStream(p))) {
            raw = in.readAllBytes();                   // InflaterInputStream = zlib decompression
        }
        int space = indexOf(raw, (byte) ' ');          // "blob| 12\0..."
        int nul   = indexOf(raw, (byte) 0);            // "blob 12|\0..."
        if (space < 0 || nul < space) throw new IllegalStateException("corrupt object: " + id);

        ObjectType type = ObjectType.fromWireName(new String(raw, 0, space));
        int size = Integer.parseInt(new String(raw, space + 1, nul - space - 1));
        int bodyLen = raw.length - nul - 1;
        if (size != bodyLen) throw new IllegalStateException("size mismatch in " + id);

        byte[] body = new byte[bodyLen];
        System.arraycopy(raw, nul + 1, body, 0, bodyLen);
        return new RawObject(type, body);
    }

    /** Expand a short id like "3b18e5" to the full 40 chars, like git does. */
    public String resolvePrefix(String prefix) throws IOException {
        if (Hashing.isFullHex(prefix)) return prefix;
        if (prefix.length() < 4 || !prefix.matches("[0-9a-f]+"))
            throw new IllegalStateException("not a valid object name: " + prefix);

        Path dir = objectsDir.resolve(prefix.substring(0, 2));
        if (!Files.isDirectory(dir)) throw new IllegalStateException("not a valid object name: " + prefix);

        String rest = prefix.substring(2);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(f -> f.getFileName().toString())
                 .filter(name -> name.length() == 38 && name.startsWith(rest))  // 38 skips leftover tmp files
                 .forEach(name -> matches.add(prefix.substring(0, 2) + name));
        }
        if (matches.isEmpty()) throw new IllegalStateException("not a valid object name: " + prefix);
        if (matches.size() > 1) throw new IllegalStateException("short id " + prefix + " is ambiguous");
        return matches.get(0);
    }

    private Path pathFor(String id) {
        return objectsDir.resolve(id.substring(0, 2)).resolve(id.substring(2));
    }

    private static int indexOf(byte[] data, byte b) {
        for (int i = 0; i < data.length; i++) if (data[i] == b) return i;
        return -1;
    }
}