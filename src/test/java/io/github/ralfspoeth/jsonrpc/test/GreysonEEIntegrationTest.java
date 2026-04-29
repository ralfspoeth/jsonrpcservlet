package io.github.ralfspoeth.jsonrpc.test;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.query.Pointer;
import io.github.ralfspoeth.json.query.Selector;
import io.github.ralfspoeth.jsonrpc.JsonRpcServlet;
import io.github.ralfspoeth.utf8.Utf8Reader;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler; // Note the 'ee11'
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

public class GreysonEEIntegrationTest {
    private static Server server;
    private static int port;

    private static final HttpClient client = HttpClient.newHttpClient();

    private final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/rpc"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");


    @BeforeAll
    static void setup() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // Add your Greyson Servlet
        context.addServlet(new ServletHolder(new JsonRpcServlet(
                Map.of("hello", p -> "hello " + p,
                        "sum", p -> switch (p) {
                            case List<?> bigDecimals -> {
                                var tmp = BigDecimal.ZERO;
                                for (var bd : bigDecimals) {
                                    if (bd instanceof BigDecimal x) {tmp = tmp.add(x);}
                                }
                                yield tmp;
                            }
                            case null, default -> BigDecimal.ZERO;
                        })
        )), "/rpc");

        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    @Test
    @DisplayName("Call sum with 10, 20, 30 yielding 60")
    void testSuccessfulRpcCall() throws Exception {
        // 1. Prepare the JSON-RPC payload
        var rq = objectBuilder().putBasic("jsonrpc", "2.0")
                .putBasic("id", "req-001")
                .putBasic("method", "sum")
                .put("params", arrayBuilder().addBasic(10).addBasic(20).addBasic(30))
                .build();

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(rq.json()))
                .build();

        // 3. Send and receive
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        AtomicReference<Exception> e = new AtomicReference<>();
        List<JsonValue> responses = new ArrayList<>();
        try (var is = response.body();
             var rdr = new Utf8Reader(is)) {
            Greyson.readValue(rdr)
                    .stream()
                    .flatMap(Selector.all())
                    .forEach(responses::add);
        } catch (Exception ex) {
            e.set(ex);
        }

        // 4. Assertions
        assertAll(
                () -> assertNull(e.get()),
                () -> assertEquals(HttpServletResponse.SC_OK, response.statusCode()),
                () -> assertEquals(1, responses.size()),
                () -> assertEquals("req-001", Pointer.self().member("id").stringValue(responses.getFirst()).orElseThrow()),
                () -> assertEquals(60, Pointer.self().member("result").intValue(responses.getFirst()).orElseThrow())
        );

    }

    @Test
    void testSingleNotification() throws IOException, InterruptedException {
        // given
        var notification = objectBuilder().putBasic("jsonrpc", "2.0").putBasic("method", "sum").build();
        var request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(notification.json())).build();
        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        // then
        var result = Greyson.readValue(new Utf8Reader(response.body()));
        assertAll(
                () -> assertTrue(result.isEmpty())
        );
    }


    @Test
    void testTwoNotifications() throws IOException, InterruptedException {
        // given
        var notification1 = objectBuilder().putBasic("jsonrpc", "2.0").putBasic("method", "sum").build();
        var notification2 = objectBuilder().putBasic("jsonrpc", "2.0").putBasic("method", "hello").build();
        var notis = arrayBuilder().add(notification1).add(notification2).build();
        var request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(notis.json())).build();
        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        // then
        var result = Greyson.readValue(new Utf8Reader(response.body()));
        assertAll(
                () -> assertTrue(result.isEmpty())
        );
    }

    @AfterAll
    static void stopJetty() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.close();
        }
    }
}