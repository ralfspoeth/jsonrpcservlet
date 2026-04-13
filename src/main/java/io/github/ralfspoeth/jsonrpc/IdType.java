package io.github.ralfspoeth.jsonrpc;

sealed interface IdType {
    record IntId(int id) implements IdType {}
    record StringId(String id) implements IdType {}
}
