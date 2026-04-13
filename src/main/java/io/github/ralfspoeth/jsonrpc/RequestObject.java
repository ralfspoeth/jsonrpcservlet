package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.json.data.Aggregate;
import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonBoolean;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record RequestObject(Basic<?> id, String method, @Nullable Aggregate params) {
    public RequestObject {
        Objects.requireNonNull(id, "id is null");
        if(id instanceof JsonBoolean) {
            throw new IllegalArgumentException("id must be a number, string, or null");
        }
        method = Objects.requireNonNull(method, "method is null");
    }
}
