package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.*;
import io.github.ralfspoeth.json.io.JsonParseException;
import io.github.ralfspoeth.json.query.Queries;
import io.github.ralfspoeth.json.query.Selector;
import io.github.ralfspoeth.utf8.Utf8Reader;
import io.github.ralfspoeth.utf8.Utf8Writer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.ralfspoeth.basix.fn.Functions.conditional;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;

public class JsonRpcServlet extends HttpServlet {

    private final Map<String, Service> dispatcher;

    public JsonRpcServlet(Map<String, Service> dispather) {
        this.dispatcher = dispather;
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var inputContentType = req.getContentType();
        if (Set.of("application/json", "text/json").contains(inputContentType)) {
            resp.setContentType("application/json");
            try (var is = req.getInputStream();
                 var rdr = new Utf8Reader(is);
                 var os = resp.getOutputStream();
                 var wrt = new Utf8Writer(os)
            ) {
                try {
                    var request = Greyson
                            .readValue(rdr)
                            .orElseThrow(() -> new JsonParseException("empty input", 0, 0));
                    boolean isBatchRequest = request instanceof JsonArray;

                    var responses = Stream.of(request)
                            .flatMap(Selector.all())
                            .parallel()
                            .map(conditional(v -> v instanceof JsonObject(var members) &&
                                            "2.0".equals(v.get("jsonrpc").map(JsonValue::string).orElse(null)) &&
                                            isValidOrNullParams(members.get("params")) && isValidOrNullId(members.get("id")),
                                    this::invokeService,
                                    this::invalidRequest
                            ))
                            .toList();
                            /*
                            .map(conditional(v -> v instanceof JsonObject(var members) &&
                                                    members.get("jsonrpc") instanceof JsonString(var s) && s.equals("2.0") &&
                                    hasValidOrNullId(v) && hasValidOrNullParams(v),
                                            q -> invokeService(method(q), params(q))
                                    ),
                                    objectBuilder().putBasic("id", null).putBasic("code", -32600).build())))
                            .filter(Objects::nonNull)
                            .toList();*/
                    System.out.println(responses);

                }
                // parse exception with code -32700
                catch (JsonParseException e) {
                    Greyson.writeBuilder(wrt, objectBuilder()
                            .putBasic("id", null)
                            .putBasic("code", -32700)
                            .putBasic("message", e.getMessage())
                    );
                }
            }
        }
    }

    private JsonObject invalidRequest(JsonValue request) {
        return objectBuilder()
                .putBasic("id", id(request))
                .putBasic("code", -32600)
                .build();
    }

    private JsonObject invokeService(JsonValue request) {
        var method = method(request);
        var id = id(request);
        var params = params(request);
        return null;
    }

    /*
                        objectBuilder()
                                .putBasic("code", -32601)
                                .putBasic("message", "No such method: " + request.method())
                                .build())*/

    private static String method(JsonValue request) {
        return request.get("method")
                .flatMap(JsonValue::string)
                .orElseThrow();
    }

    private static @Nullable Object id(JsonValue request) {
        return request.get("id")
                .flatMap(id -> switch (id) {
                    case JsonNumber(var n) -> Optional.of(n);
                    case JsonString(var s) -> Optional.of(s);
                    default -> Optional.empty();
                })
                .orElse(null);
    }

    private static @Nullable Object params(JsonValue request) {
        return request.get("params")
                .map(Queries::asObject)
                .orElse(null);
    }

    private static boolean isValidOrNullParams(@Nullable JsonValue params) {
        return params == null || params instanceof Aggregate;
    }

    private static boolean isValidOrNullId(@Nullable JsonValue id) {
        return id == null || id instanceof JsonNumber || id instanceof JsonString;
    }
}
