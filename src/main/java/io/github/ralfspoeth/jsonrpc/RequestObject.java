package io.github.ralfspoeth.jsonrpc;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record RequestObject(@Nullable IdType id, String method, @Nullable ParamType params) {
    public RequestObject {
        method = Objects.requireNonNull(method, "method is null");
    }
}
