package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.Aggregate;
import io.github.ralfspoeth.json.data.JsonValue;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static io.github.ralfspoeth.json.data.Builder.objectBuilder;

public class JsonRpcServlet extends HttpServlet {

    private final Function<RequestObject, @Nullable ResponseObject> handler;

    public JsonRpcServlet(Function<RequestObject, ResponseObject> handler) {this.handler = handler;}

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
                var responses = Greyson.readValue(rdr)
                        .stream()
                        .flatMap(Selector.all())
                        .parallel()
                        .filter(r -> r.get("jsonrpc").flatMap(JsonValue::string).filter("2.0"::equals).isPresent())
                        .map(r -> new RequestObject(
                                r.get("id").flatMap(JsonValue::basic).orElseThrow(),
                                r.get("method").flatMap(JsonValue::string).orElseThrow(),
                                r.get("params").map(Aggregate.class::cast).orElse(null)
                        ))
                        .map(handler)
                        .filter(Objects::nonNull)
                        .toList();
                var ja = responses.stream()
                        .map(ro -> objectBuilder()
                        .putBasic("jsonrpc", "2.0")
                        .put("id", ro.id())
                        .put(ro.error() == null ? "result" : "error",
                                ro.error() == null ? Objects.requireNonNull(ro.result()) : ro.error()
                        ).build())
                        .collect(Queries.toJsonArray());
                if(ja.size() < 2) {
                    ja.get(0).ifPresent(r -> Greyson.writeValue(wrt, r));
                } else {
                    Greyson.writeValue(wrt, ja);
                }
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
