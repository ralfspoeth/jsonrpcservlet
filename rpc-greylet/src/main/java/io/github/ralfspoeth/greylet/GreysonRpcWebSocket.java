package io.github.ralfspoeth.greylet;

import io.github.ralfspoeth.greysonrpc.GreysonRpcProcessor;
import io.github.ralfspoeth.json.data.JsonValue;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.websocket.OnMessage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A WebSocket endpoint that handles JSON-RPC 2.0 requests delivered as
 * text messages, with a Greyson-native business function &mdash; the
 * Greyson types are part of this API on purpose. Prefer the
 * {@code rpc-servlet} module for a completely Greyson-free API.
 * <p>
 * The endpoint is concrete and can be used in two ways: instantiated
 * or subclassed programmatically with the business-function constructor,
 * or deployed in a CDI-enabled container, where the business function is
 * injected from a producer of type
 * {@code BiFunction<String, JsonValue, JsonValue>}. Either way it must
 * be registered under a path, e.g. by a subclass annotated with
 * {@link jakarta.websocket.server.ServerEndpoint} or programmatically
 * via {@link jakarta.websocket.server.ServerEndpointConfig}.
 * <p>
 * See {@link GreysonRpcProcessor} for the exception-to-error-code
 * mapping. Note that the requests of a batch are processed concurrently,
 * so the business function must tolerate concurrent invocation.
 * <p>
 * If the incoming message is a notification (or a batch consisting solely
 * of notifications), the returned string is empty and no message is sent
 * back to the peer.
 *
 * @see GreysonRpcServlet
 * @see GreysonRpcProcessor
 */
public class GreysonRpcWebSocket {

    private volatile @Nullable GreysonRpcProcessor processor;

    /**
     * Constructs an endpoint without a business function, intended for
     * CDI-enabled containers which inject one after instantiation.
     */
    public GreysonRpcWebSocket() {
    }

    /**
     * Constructs a new GreysonRpcWebSocket with the given business function.
     *
     * @param businessFunction maps method name and params to the result
     */
    public GreysonRpcWebSocket(BiFunction<String, JsonValue, JsonValue> businessFunction) {
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

    /**
     * Handles an incoming WebSocket text message containing a JSON-RPC 2.0
     * request, batch request, or notification.
     *
     * @param message the raw JSON text received from the WebSocket peer.
     * @return the serialized JSON-RPC response, or an empty string if the
     * request was a notification (or a batch of notifications).
     * @throws IOException if reading or writing the JSON payload fails.
     */
    @OnMessage
    public String onMessage(String message) throws IOException {
        var p = Objects.requireNonNull(processor, "no business function configured");
        try (var rdr = Reader.of(message); var wrtr = new StringWriter()) {
            p.process(rdr, wrtr);
            return wrtr.toString();
        }
    }
}
