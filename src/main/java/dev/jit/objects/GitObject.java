package dev.jit.objects;

import java.nio.charset.StandardCharsets;

/** Anything stored in .jit/objects. Blob and Tree now; Commit in a later milestone. */
public interface GitObject {

    ObjectType type();

    /** The content only, without the header. */
    byte[] body();

    /** Header + body: exactly the bytes that get hashed and compressed. */
    default byte[] serialize() {
        byte[] body = body();
        byte[] header = (type().wireName() + " " + body.length + "\0")
                .getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);             // copy header to the start
        System.arraycopy(body, 0, out, header.length, body.length);     // copy body right after it
        return out;
    }
}