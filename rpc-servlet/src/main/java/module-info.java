import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.ralfspoeth.rpcservlet {
    exports io.github.ralfspoeth.jsonrpc;
    // transitive: the exported classes extend greylet's — which makes
    // Greyson readable to consumers via greylet's transitive requires;
    // the Procedure-based API itself remains Greyson-free
    requires transitive io.github.ralfspoeth.greylet;
    // used internally by the Dispatch bridge
    requires io.github.ralfspoeth.greyson;
    // CDI is optional: only needed when deployed in a CDI container
    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static org.jspecify;
}
