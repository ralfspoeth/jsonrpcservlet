package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.query.Queries;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Internal bridge from the Greyson-free {@link Procedure} API to the
 * Greyson-native business function of the JSON-RPC engine. Unknown
 * methods map to -32601 (method not found); runtime exceptions thrown
 * by procedures propagate unwrapped so the engine's spec error-code
 * mapping applies.
 */
final class Dispatch {

    private Dispatch() {
        // no instances
    }

    static BiFunction<String, JsonValue, JsonValue> of(Map<String, Procedure> dispatcher) {
        Objects.requireNonNull(dispatcher);
        return (method, params) -> {
            var procedure = dispatcher.get(method);
            if (procedure == null) {
                throw new NoSuchElementException("Method not found: " + method);
            }
            try {
                return JsonValue.of(procedure.request(Queries.asObject(params)));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
