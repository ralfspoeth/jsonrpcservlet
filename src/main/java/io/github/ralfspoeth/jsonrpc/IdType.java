package io.github.ralfspoeth.jsonrpc;

sealed interface IdType {
    record IntId(int id) implements IdType {
        @Override
        public String toString() {
            return Integer.toString(id);
        }
    }
    record StringId(String id) implements IdType {
        @Override
        public String toString() {
            return id;
        }
    }
}
