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

public class HubClientTest {

    // parseHub - pure JSON parsing, no network
    @Nested
    @DisplayName("parseHub")
    class ParseHubTests {

        @Test
        void parsesAllFields() throws Exception {
            String json = "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}";

            HubRecord hub = HubClient.parseHub(json);

            assertEquals("H-500", hub.getHubId());
            assertEquals("Gauteng", hub.getProvince());
            assertEquals("Johannesburg Central", hub.getSortingCenter());
        }

        @Test
        void malformedJsonThrows() {
            assertThrows(Exception.class, () -> HubClient.parseHub("not json"));
        }
    }

    
    // fetchHub - real HTTP call, against a local stub standing in for hub-service
    
    @Nested
    @DisplayName("fetchHub")
    class FetchHubTests {

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
        void returnsHubOnSuccess() throws Exception {
            String baseUrl = startStub("/hubs/H-500", 200,
                    "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

            Optional<HubRecord> hub = HubClient.fetchHub(baseUrl, "H-500");

            assertTrue(hub.isPresent());
            assertEquals("H-500", hub.get().getHubId());
        }

        @Test
        void notFoundReturnsEmptyRatherThanThrowing() throws Exception {
            String baseUrl = startStub("/hubs/H-999", 404, "{\"error\":\"No hub found with id H-999\"}");

            Optional<HubRecord> hub = HubClient.fetchHub(baseUrl, "H-999");

            assertTrue(hub.isEmpty());
        }

        @Test
        void serverErrorThrowsIOException() throws Exception {
            String baseUrl = startStub("/hubs/H-500", 500, "internal error");

            assertThrows(IOException.class, () -> HubClient.fetchHub(baseUrl, "H-500"));
        }

        @Test
        void unreachableHostThrows() {
            assertThrows(Exception.class, () -> HubClient.fetchHub("http://localhost:1", "H-500"));
        }
    }
}