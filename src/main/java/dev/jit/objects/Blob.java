package dev.jit.objects;

/** File contents, nothing else. The filename lives in the tree that points here. */
public record Blob(byte[] data) implements GitObject {
    @Override public ObjectType type() { return ObjectType.BLOB; }
    @Override public byte[] body()     { return data; }
}