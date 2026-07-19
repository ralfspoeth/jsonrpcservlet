package io.github.ralfspoeth.jsonrpc;

import io.github.ralfspoeth.greylet.GreysonRpcServlet;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * A servlet that handles JSON-RPC 2.0 requests.
 * It dispatches requests to registered {@link Procedure} implementations;
 * the transport handling is inherited from {@link GreysonRpcServlet}.
 * <p>
 * The servlet is concrete: either instantiate it programmatically with
 * the dispatcher constructor, or deploy it as-is in a CDI-enabled
 * container with a producer of type {@code Map<String, Procedure>}.
 * In a CDI container, such a dispatcher producer takes precedence over
 * a business-function producer for the superclass.
 */
public class JsonRpcServlet extends GreysonRpcServlet {

    /**
     * Constructs a servlet without a dispatcher, intended for
     * CDI-enabled containers which inject one after instantiation.
     */
    public JsonRpcServlet() {
    }

    /**
     * Constructs a new JsonRpcServlet with the given procedure dispatcher.
     *
     * @param dispatcher A map where keys are method names and values are {@link Procedure} instances.
     */
    public JsonRpcServlet(Map<String, Procedure> dispatcher) {
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
