package io.github.ralfspoeth.greysonrpc.test;

import io.github.ralfspoeth.greysonrpc.GreysonRpcProcessor;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link GreysonRpcProcessor} logging contract against the
 * JUL backend of {@link System.Logger}, with the logger configured at
 * each of the three relevant levels: {@code FINE} (= debug), {@code INFO},
 * and {@code WARNING}.
 */
class GreysonRpcProcessorLoggingTest {

    // strong reference: the JUL logger backing
    // System.getLogger(GreysonRpcProcessor.class.getName())
    private static final Logger JUL = Logger.getLogger(GreysonRpcProcessor.class.getName());

    private static final Formatter FORMATTER = new SimpleFormatter();

    /**
     * Collects log records; thread-safe because batch elements are
     * processed concurrently.
     */
    private static class RecordingHandler extends Handler {
        final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private final RecordingHandler handler = new RecordingHandler();

    private final GreysonRpcProcessor processor = new GreysonRpcProcessor((method, params) -> switch (method) {
        case "echo" -> params;
        case "picky" -> throw new IllegalArgumentException("picky");
        case "boom" -> throw new IllegalStateException("boom");
        default -> throw new NoSuchElementException(method);
    });

    @BeforeEach
    void installHandler() {
        handler.setLevel(Level.ALL);
        JUL.setUseParentHandlers(false);
        JUL.addHandler(handler);
    }

    @AfterEach
    void removeHandler() {
        JUL.removeHandler(handler);
        JUL.setLevel(null); // back to inherited level
        JUL.setUseParentHandlers(true);
    }

    // -------------------------- FINE: all three levels pass ----------

    @Test
    @DisplayName("At FINE, a successful call logs the invocation (INFO) and the result (FINE)")
    void fineLevelLogsInvocationAndResult() throws IOException {
        JUL.setLevel(Level.FINE);
        var response = process(request("echo", 1));

        assertAll(
                () -> assertTrue(response.contains("result"), "sanity: call succeeded"),
                () -> assertTrue(messagesAt(Level.INFO).stream()
                                .anyMatch(m -> m.contains("invoking echo")),
                        "invocation with name and params at info"),
                () -> assertTrue(messagesAt(Level.FINE).stream()
                                .anyMatch(m -> m.contains("invoked echo")),
                        "result with name, params and result at debug/fine"),
                () -> assertTrue(messagesAt(Level.WARNING).isEmpty(),
                        "no warnings on success")
        );
    }

    @Test
    @DisplayName("At FINE, errors additionally log warnings with the causing exception")
    void fineLevelLogsWarningsOnErrors() throws IOException {
        JUL.setLevel(Level.FINE);
        process(request("nope", 2));

        var warnings = recordsAt(Level.WARNING);
        assertAll(
                () -> assertTrue(warnings.stream()
                                .map(FORMATTER::formatMessage)
                                .anyMatch(m -> m.contains("method not found: nope")),
                        "unknown method warned"),
                () -> assertTrue(warnings.stream()
                                .anyMatch(r -> r.getThrown() instanceof NoSuchElementException),
                        "causing exception attached to the record")
        );
    }

    // -------------------------- INFO: no debug ------------------------

    @Test
    @DisplayName("At INFO, invocations are logged but results are not")
    void infoLevelSuppressesDebug() throws IOException {
        JUL.setLevel(Level.INFO);
        process(request("echo", 3));

        assertAll(
                () -> assertTrue(messagesAt(Level.INFO).stream()
                                .anyMatch(m -> m.contains("invoking echo")),
                        "invocation still logged"),
                () -> assertTrue(recordsAt(Level.FINE).isEmpty(),
                        "debug suppressed")
        );
    }

    // -------------------------- WARNING: errors only -------------------

    @Test
    @DisplayName("At WARNING, successful calls are silent")
    void warningLevelSilentOnSuccess() throws IOException {
        JUL.setLevel(Level.WARNING);
        process(request("echo", 4));

        assertTrue(handler.records.isEmpty(), "nothing logged for a successful call");
    }

    @Test
    @DisplayName("At WARNING, every error path leaves a warning")
    void warningLevelLogsAllErrorPaths() throws IOException {
        JUL.setLevel(Level.WARNING);

        process(request("picky", 5));       // -32602
        process(request("boom", 6));        // -32603
        processRaw("this is not json");     // -32700
        processRaw("42");                   // -32600, top-level basic value

        var messages = messagesAt(Level.WARNING);
        assertAll(
                () -> assertTrue(messages.stream().anyMatch(m -> m.contains("invalid params for picky"))),
                () -> assertTrue(messages.stream().anyMatch(m -> m.contains("internal error in boom"))),
                () -> assertTrue(messages.stream().anyMatch(m -> m.contains("Parse error"))),
                () -> assertTrue(messages.stream().anyMatch(m -> m.contains("Invalid Request")))
        );
    }

    @Test
    @DisplayName("A failing notification warns although the peer gets no response")
    void failedNotificationWarns() throws IOException {
        JUL.setLevel(Level.WARNING);
        var notification = objectBuilder()
                .putBasic("jsonrpc", "2.0")
                .putBasic("method", "boom")
                .build();
        var response = process(notification);

        assertAll(
                () -> assertTrue(response.isEmpty(), "notifications never get a response"),
                () -> assertTrue(messagesAt(Level.WARNING).stream()
                                .anyMatch(m -> m.contains("notification boom failed")),
                        "failure logged nonetheless")
        );
    }

    // -------------------------- helpers --------------------------

    private static JsonValue request(String method, int id) {
        return objectBuilder()
                .putBasic("jsonrpc", "2.0")
                .putBasic("method", method)
                .putBasic("id", id)
                .build();
    }

    private String process(JsonValue value) throws IOException {
        return processRaw(value.json());
    }

    private String processRaw(String text) throws IOException {
        try (var rdr = new StringReader(text); var wrt = new StringWriter()) {
            processor.process(rdr, wrt);
            return wrt.toString();
        }
    }

    private List<LogRecord> recordsAt(Level level) {
        return handler.records.stream()
                .filter(r -> r.getLevel().equals(level))
                .toList();
    }

    private List<String> messagesAt(Level level) {
        return recordsAt(level).stream()
                .map(FORMATTER::formatMessage)
                .toList();
    }
}
