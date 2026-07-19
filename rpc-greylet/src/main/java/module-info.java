import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.ralfspoeth.greylet {
    exports io.github.ralfspoeth.greylet;
    requires transitive jakarta.servlet;
    requires transitive jakarta.websocket;
    // the engine and, through it, Greyson are part of this
    // module's API — on purpose
    requires transitive io.github.ralfspoeth.greysonrpc;
    // CDI is optional: only needed when deployed in a CDI container
    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static org.jspecify;
    requires io.github.ralfspoeth.utf8io;
}
