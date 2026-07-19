package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.greylet.GreysonRpcWebSocket;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * A WebSocket endpoint that handles JSON-RPC 2.0 requests delivered as
 * text messages; the transport handling is inherited from
 * {@link GreysonRpcWebSocket}.
 * <p>
 * The endpoint is concrete: either instantiate or subclass it
 * programmatically with the dispatcher constructor, or deploy it in a
 * CDI-enabled container with a producer of type
 * {@code Map<String, Procedure>}. It must be registered under a path,
 * e.g. by a subclass annotated with
 * {@link jakarta.websocket.server.ServerEndpoint} or programmatically
 * via {@link jakarta.websocket.server.ServerEndpointConfig}.
 *
 * @see JsonRpcServlet
 * @see Procedure
 */
public class JsonRpcWebSocket extends GreysonRpcWebSocket {

    /**
     * Constructs an endpoint without a dispatcher, intended for
     * CDI-enabled containers which inject one after instantiation.
     */
    public JsonRpcWebSocket() {
    }

    /**
     * Constructs a new JsonRpcWebSocket with the given procedure dispatcher.
     *
     * @param dispatcher A map where keys are method names and values are
     *                   {@link Procedure} instances handling the corresponding calls.
     */
    public JsonRpcWebSocket(Map<String, Procedure> dispatcher) {
        super(Dispatch.of(dispatcher));
    }

    /**
     * CDI initializer: adopts an injected procedure dispatcher.
     * Runs after the superclass initializer, so a dispatcher producer
     * wins over a business-function producer.
     */
    @Inject
    public void initDispatcher(Instance<Map<String, Procedure>> dispatcher) {
        if (dispatcher.isResolvable()) {
            setBusinessFunction(Dispatch.of(dispatcher.get()));
        }
    }
}
