package io.github.ralfspoeth.greylet;

import io.github.ralfspoeth.greysonrpc.GreysonRpcProcessor;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.utf8.Utf8Reader;
import io.github.ralfspoeth.utf8.Utf8Writer;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A servlet that handles JSON-RPC 2.0 requests with a Greyson-native
 * business function &mdash; the Greyson types are part of this API
 * on purpose. Prefer the {@code rpc-servlet} module for a completely
 * Greyson-free API.
 * <p>
 * The servlet is concrete and can be used in two ways: instantiated
 * programmatically with the business-function constructor, or deployed
 * as-is in a CDI-enabled container, where the business function is
 * injected from a producer of type
 * {@code BiFunction<String, JsonValue, JsonValue>}.
 * <p>
 * The business function receives the method name and the {@code params}
 * value and returns the result as a {@link JsonValue}; see
 * {@link GreysonRpcProcessor} for the exception-to-error-code mapping.
 * Note that the requests of a batch are processed concurrently, so the
 * business function must tolerate concurrent invocation.
 */
public class GreysonRpcServlet extends HttpServlet {

    /**
     * The MIME type for JSON content.
     */
    private static final String JSON_CONTENT_TYPE = "application/json";

    private volatile @Nullable GreysonRpcProcessor processor;

    /**
     * Constructs a servlet without a business function, intended for
     * CDI-enabled containers which inject one after instantiation.
     */
    public GreysonRpcServlet() {
    }

    /**
     * Constructs a new GreysonRpcServlet with the given business function.
     *
     * @param businessFunction maps method name and params to the result
     */
    public GreysonRpcServlet(BiFunction<String, JsonValue, JsonValue> businessFunction) {
        setBusinessFunction(businessFunction);
    }

    /**
     * CDI initializer: adopts an injected business function unless one
     * has already been provided through the constructor. Optional by way
     * of {@link Instance}, so deployments without a matching producer
     * (e.g. subclasses configured differently) remain valid.
     */
    @Inject
    public void initBusinessFunction(Instance<BiFunction<String, JsonValue, JsonValue>> businessFunction) {
        if (processor == null && businessFunction.isResolvable()) {
            setBusinessFunction(businessFunction.get());
        }
    }

    /**
     * (Re-)configures the business function; used by the constructor,
     * the CDI initializer, and subclasses.
     */
    protected final void setBusinessFunction(BiFunction<String, JsonValue, JsonValue> businessFunction) {
        this.processor = new GreysonRpcProcessor(Objects.requireNonNull(businessFunction));
    }

    @Override
    public void init() throws ServletException {
        if (processor == null) {
            throw new ServletException(
                    "no business function configured - pass one to the constructor or provide a CDI producer");
        }
    }

    /**
     * Handles HTTP POST requests for JSON-RPC.
     * It reads the JSON-RPC request from the input stream, processes it, and writes the response to the output stream.
     *
     * @param req  The HttpServletRequest object that contains the request the client has made of the servlet.
     * @param resp The HttpServletResponse object that contains the response the servlet sends to the client.
     * @throws IOException If the request for the POST could not be handled.
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (JSON_CONTENT_TYPE.equals(req.getContentType())) {
            resp.setContentType(JSON_CONTENT_TYPE);
            try (var rdr = new Utf8Reader(req.getInputStream());
                 var wrt = new Utf8Writer(resp.getOutputStream())
            ) {
                Objects.requireNonNull(processor, "no business function configured").process(rdr, wrt);
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
