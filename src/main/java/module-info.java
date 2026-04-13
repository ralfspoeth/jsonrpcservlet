import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.ralfspoeth.rpcservlet {
    requires jakarta.servlet;
    requires io.github.ralfspoeth.greyson;
    requires io.github.ralfspoeth.basix;
    requires io.github.ralfspoeth.utf8io;
    requires org.jspecify;
}