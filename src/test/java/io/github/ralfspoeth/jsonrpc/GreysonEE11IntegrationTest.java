package io.github.ralfspoeth.jsonrpc;

import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler; // Note the 'ee11'
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class GreysonEE11IntegrationTest {
    private static Server server;
    private static int port;

    @BeforeAll
    static void setup() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        // Explicitly using the EE11 environment handler
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // Add your Greyson Servlet
        context.addServlet(new ServletHolder(new JsonRpcServlet(_->null)), "/rpc");

        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    @Test
    void testGreyson() throws Exception {

    }


    @Test
    @DisplayName("Should process a valid JSON-RPC 2.0 request via Greyson")
    void testSuccessfulRpcCall() throws Exception {
        // 1. Prepare the JSON-RPC payload
        String jsonPayload = """
            {
                "jsonrpc": "2.0",
                "method": "calculateSum",
                "params": [10, 20, 30],
                "id": "req-001"
            }
            """;

        // 2. Build the modern HTTP Client
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/rpc"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // 3. Send and receive
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Assertions
            assertEquals(HttpServletResponse.SC_OK, response.statusCode());
            assertNotNull(response.body());
            assertTrue(response.body().contains("\"id\":\"req-001\""), "Response should match request ID");
            assertTrue(response.body().contains("60"), "Result should be the sum of params");
        }
    }

    @AfterAll
    static void stopJetty() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}