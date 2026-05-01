package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.*;
import io.github.ralfspoeth.json.io.JsonParseException;
import io.github.ralfspoeth.json.query.Queries;
import io.github.ralfspoeth.json.query.Selector;
import io.github.ralfspoeth.utf8.Utf8Reader;
import io.github.ralfspoeth.utf8.Utf8Writer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.objectBuilder;

public class JsonRpcProcessor {
    /**
     * The map of service implementations indexed by method name.
     */
    private final Map<String, Service> dispatcher;

    public JsonRpcProcessor(Map<String, Service> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void processRequest(Utf8Reader rdr, Utf8Writer wrt) throws IOException {
        try {
            var request = Greyson
                    .readValue(rdr)
                    .orElseThrow(() -> new JsonParseException("empty input", 0, 0));

            // empty batch request
            if (request instanceof JsonArray(var l) && l.isEmpty()) {
                Greyson.writeValue(wrt, invalidRequest("Empty Batch Request"));
            } else {
                // invoke the services for each valid request in parallel
                var responses = Stream.of(request)
                        .flatMap(Selector.all()) // unfolds a batch request
                        .parallel()
                        .map(r -> isValid(r) ?
                                invokeService(r) :
                                invalidRequest("Invalid request object")
                        )
                        .filter(Objects::nonNull) // return value from notifications
                        .toList();

                // no empty arrays
                if (!responses.isEmpty()) {
                    // as array if and only if the request has been a batch request
                    if (request instanceof JsonArray) {
                        Greyson.writeValue(wrt, responses.stream().collect(Queries.toJsonArray()));
                    } else {
                        assert responses.size() == 1;
                        Greyson.writeValue(wrt, responses.getFirst());
                    }
                }
            }
        }
        // parse exception with code -32700
        catch (JsonParseException e) {
            Greyson.writeValue(wrt, parseError(e));
        }
    }

    /**
     * Checks if a given {@link JsonValue} represents a valid JSON-RPC request.
     *
     * @param request The {@link JsonValue} to validate.
     * @return {@code true} if the request is valid, {@code false} otherwise.
     */
    static boolean isValid(JsonValue request) {
        return request instanceof JsonObject(var members) &&
                request.get("jsonrpc").flatMap(JsonValue::string).orElse("").equals("2.0") &&
                request.get("method").filter(JsonString.class::isInstance).isPresent() &&
                isValidOrNullId(members.get("id")) &&
                isValidOrNullParams(members.get("params"));
    }

    /**
     * Creates an {@link JsonObject} representing an "Invalid Request" error response.
     *
     * @return An {@link JsonObject} with error code -32600.
     */
    static JsonObject invalidRequest(String message) {
        return Builder.objectBuilder()
                .putBasic("jsonrpc", "2.0")
                .putBasic("id", null)
                .put("error", Builder.objectBuilder()
                        .putBasic("code", -32600)
                        .putBasic("message", message))
                .build();
    }

    /**
     * Invokes the appropriate service method based on the JSON-RPC request.
     *
     * @param request The {@link JsonValue} representing the JSON-RPC request.
     * @return A {@link JsonObject} representing the JSON-RPC response, or {@code null} if it's a notification.
     */
    @Nullable JsonObject invokeService(JsonValue request) {
        var method = method(request);
        var id = id(request);
        var params = params(request);
        var service = dispatcher.get(method);

        // notification
        if (id == null) {
            if (service != null) {
                try {
                    service.notification(params);
                } catch (Exception _) {
                    // ignore silently
                }
            }
            return null;
        } else {
            var ob = Builder.objectBuilder()
                    .putBasic("jsonrpc", "2.0")
                    .putBasic("id", id);
            if (service == null) {
                ob.put("error", Builder.objectBuilder()
                        .putBasic("code", -32601)
                        .putBasic("message", "No such method: " + method));
            } else {
                try {
                    var result = service.request(params);
                    ob.putBasic("result", result);

                } catch (Exception ex) {
                    ob.put("error", Builder.objectBuilder()
                            .putBasic("code", -32000)
                            .putBasic("message", ex.getMessage()));
                }
            }
            return ob.build();
        }
    }

    private static JsonObject parseError(JsonParseException e) {
        return objectBuilder()
                .putBasic("jsonrpc", "2.0")
                .putBasic("id", null)
                .put("error", objectBuilder()
                        .putBasic("code", -32700)
                        .putBasic("message", e.getMessage())
                ).build();
    }

    /**
     * Extracts the method name from a JSON-RPC request.
     *
     * @param request The {@link JsonValue} representing the JSON-RPC request.
     * @return The method name as a {@link String}.
     * @throws NoSuchElementException If the "method" field is not found.
     */
    static String method(JsonValue request) {
        return request.get("method")
                .flatMap(JsonValue::string)
                .orElseThrow();
    }

    /**
     * Extracts the ID from a JSON-RPC request.
     *
     * @param request The {@link JsonValue} representing the JSON-RPC request.
     * @return The ID as an {@link Object} (either a {@link String} or a {@link Number}), or {@code null} if not present.
     */
    static @Nullable Object id(JsonValue request) {
        return request.get("id")
                .flatMap(id -> switch (id) {
                    case JsonNumber(var n) -> Optional.of(n);
                    case JsonString(var s) -> Optional.of(s);
                    default -> Optional.empty();
                })
                .orElse(null);
    }

    /**
     * Extracts the parameters from a JSON-RPC request.
     *
     * @param request The {@link JsonValue} representing the JSON-RPC request.
     * @return The parameters as an {@link Object}, or {@code null} if not present.
     */
    static @Nullable Object params(JsonValue request) {
        return request.get("params")
                .map(Queries::asObject)
                .orElse(null);
    }

    /**
     * Checks if the "params" field in a JSON-RPC request is valid or null.
     * Valid parameters can be an {@link Aggregate} (JsonArray or JsonObject) or {@code null}.
     *
     * @param params The {@link JsonValue} representing the "params" field.
     * @return {@code true} if the parameters are valid or null, {@code false} otherwise.
     */
    static boolean isValidOrNullParams(@Nullable JsonValue params) {
        return params == null || params instanceof Aggregate;
    }

    /**
     * Checks if the "id" field in a JSON-RPC request is valid or null.
     * Valid IDs can be a {@link JsonNumber}, {@link JsonString}, or {@link JsonNull}.
     *
     * @param id The {@link JsonValue} representing the "id" field.
     * @return {@code true} if the ID is valid or null, {@code false} otherwise.
     */
    static boolean isValidOrNullId(@Nullable JsonValue id) {
        return id == null || id instanceof JsonNumber || id instanceof JsonString || id instanceof JsonNull;
    }
}