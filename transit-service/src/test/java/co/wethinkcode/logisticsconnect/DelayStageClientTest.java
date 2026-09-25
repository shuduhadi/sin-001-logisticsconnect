package co.wethinkcode.logisticsconnect;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DelayStageClientTest {

    
    // parseStage - pure JSON parsing, no network
    @Nested
    @DisplayName("parseStage")
    class ParseStageTests {

        @Test
        void parsesTheStageField() throws Exception {
            assertEquals(3, DelayStageClient.parseStage("{\"hubId\":\"H-500\",\"stage\":3}"));
        }

        @Test
        void malformedJsonThrows() {
            assertThrows(Exception.class, () -> DelayStageClient.parseStage("not json"));
        }
    }

    
    // fetchStage - real HTTP call, against a local stub standing in for delay-stage-service
   
    @Nested
    @DisplayName("fetchStage")
    class FetchStageTests {

        private HttpServer stubServer;

        @AfterEach
        void tearDown() {
            if (stubServer != null) stubServer.stop(0);
        }

        private String startStub(String path, int statusCode, String body) throws IOException {
            stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stubServer.createContext(path, exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            stubServer.start();
            return "http://localhost:" + stubServer.getAddress().getPort();
        }

        @Test
        void returnsStageOnSuccess() throws Exception {
            String baseUrl = startStub("/delay-stage/H-500", 200, "{\"hubId\":\"H-500\",\"stage\":5}");

            Optional<Integer> stage = DelayStageClient.fetchStage(baseUrl, "H-500");

            assertEquals(Optional.of(5), stage);
        }

        @Test
        void notFoundReturnsEmptyRatherThanThrowing() throws Exception {
            String baseUrl = startStub("/delay-stage/H-999", 404, "{\"error\":\"No delay stage recorded for hub H-999\"}");

            Optional<Integer> stage = DelayStageClient.fetchStage(baseUrl, "H-999");

            assertTrue(stage.isEmpty());
        }

        @Test
        void serverErrorThrowsIOException() throws Exception {
            String baseUrl = startStub("/delay-stage/H-500", 500, "internal error");

            assertThrows(IOException.class, () -> DelayStageClient.fetchStage(baseUrl, "H-500"));
        }

        @Test
        void unreachableHostThrows() {
            assertThrows(Exception.class, () -> DelayStageClient.fetchStage("http://localhost:1", "H-500"));
        }
    }
}