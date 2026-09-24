package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpServer;

import co.wethinkcode.logisticsconnect.HubRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IngestionClientTest {

    // parseHubs - pure JSON parsing, no network
    
    @Nested
    @DisplayName("parseHubs")
    class ParseHubsTests {

        private static final String SAMPLE_JSON = """
                [
                  {
                    "hubId": "H-500",
                    "province": "Gauteng",
                    "sortingCenter": "Johannesburg Central",
                    "active": true,
                    "mergedFrom": ["H-504", "H-510", "H-515"]
                  },
                  {
                    "hubId": "H-511",
                    "province": "Limpopo",
                    "sortingCenter": "Polokwane Hub",
                    "active": null,
                    "mergedFrom": []
                  }
                ]
                """;

        @Test
        void parsesAllRecordsInTheArray() throws JsonProcessingException {
            List<HubRecord> hubs = IngestionClient.parseHubs(SAMPLE_JSON);
            assertEquals(2, hubs.size());
        }

        @Test
        void mapsEveryFieldCorrectly() throws JsonProcessingException {
            List<HubRecord> hubs = IngestionClient.parseHubs(SAMPLE_JSON);
            HubRecord jhb = hubs.get(0);

            assertEquals("H-500", jhb.getHubId());
            assertEquals("Gauteng", jhb.getProvince());
            assertEquals("Johannesburg Central", jhb.getSortingCenter());
            assertEquals(Boolean.TRUE, jhb.getActive());
            assertEquals(List.of("H-504", "H-510", "H-515"), jhb.getMergedFrom());
        }

        @Test
        void nullActiveIsPreservedAsNullNotFalse() throws JsonProcessingException {
            List<HubRecord> hubs = IngestionClient.parseHubs(SAMPLE_JSON);
            assertNull(hubs.get(1).getActive());
        }

        @Test
        void emptyArrayParsesToEmptyList() throws JsonProcessingException {
            List<HubRecord> hubs = IngestionClient.parseHubs("[]");
            assertTrue(hubs.isEmpty());
        }

        @Test
        void malformedJsonThrowsRatherThanReturningNullOrPartialData() {
            assertThrows(JsonProcessingException.class, () -> IngestionClient.parseHubs("not valid json"));
        }
    }

    // fetchHubs - real HTTP call, against a local stub server standing in
    // for ingestion-service (no live ingestion-service needed to run these)
    @Nested
    @DisplayName("fetchHubs")
    class FetchHubsTests {

        private HttpServer stubServer;

        @AfterEach
        void tearDown() {
            if (stubServer != null) {
                stubServer.stop(0);
            }
        }

        private String startStub(int statusCode, String responseBody) throws IOException {
            stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stubServer.createContext("/hubs", exchange -> {
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            stubServer.start();
            return "http://localhost:" + stubServer.getAddress().getPort();
        }

        @Test
        void returnsParsedHubsOnSuccess() throws Exception {
            String baseUrl = startStub(200,
                    "[{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}]");

            List<HubRecord> hubs = IngestionClient.fetchHubs(baseUrl);

            assertEquals(1, hubs.size());
            assertEquals("H-500", hubs.get(0).getHubId());
        }

        @Test
        void nonTwoHundredStatusThrowsIOException() throws Exception {
            String baseUrl = startStub(500, "internal error");

            assertThrows(IOException.class, () -> IngestionClient.fetchHubs(baseUrl));
        }

        @Test
        void unreachableHostThrowsRatherThanHanging() {
            // nothing listening on this port
            String baseUrl = "http://localhost:1";

            assertThrows(Exception.class, () -> IngestionClient.fetchHubs(baseUrl));
        }
    }
}