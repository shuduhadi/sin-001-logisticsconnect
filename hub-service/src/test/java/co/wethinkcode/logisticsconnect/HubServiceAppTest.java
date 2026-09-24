package co.wethinkcode.logisticsconnect;

import com.sun.net.httpserver.HttpServer;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class HubServiceAppTest {


    // findHubById - pure lookup logic, no network
  
    @Nested
    @DisplayName("findHubById")
    class FindHubByIdTests {

        private HubRecord hub(String hubId, String sortingCenter) {
            HubRecord h = new HubRecord();
            h.setHubId(hubId);
            h.setProvince("Gauteng");
            h.setSortingCenter(sortingCenter);
            h.setActive(true);
            h.setMergedFrom(new ArrayList<>());
            return h;
        }

        @Test
        void findsHubByExactId() {
            List<HubRecord> hubs = List.of(hub("H-500", "Johannesburg Central"), hub("H-503", "Durban Harbour"));
            Optional<HubRecord> result = HubServiceApp.findHubById(hubs, "H-500");
            assertTrue(result.isPresent());
            assertEquals("Johannesburg Central", result.get().getSortingCenter());
        }

        @Test
        void lookupIsCaseInsensitive() {
            List<HubRecord> hubs = List.of(hub("H-500", "Johannesburg Central"));
            assertTrue(HubServiceApp.findHubById(hubs, "h-500").isPresent());
        }

        @Test
        void unknownHubIdReturnsEmpty() {
            List<HubRecord> hubs = List.of(hub("H-500", "Johannesburg Central"));
            assertTrue(HubServiceApp.findHubById(hubs, "H-999").isEmpty());
        }

        @Test
        void nullHubIdReturnsEmptyRatherThanThrowing() {
            List<HubRecord> hubs = List.of(hub("H-500", "Johannesburg Central"));
            assertTrue(HubServiceApp.findHubById(hubs, null).isEmpty());
        }

        @Test
        void emptyHubListReturnsEmpty() {
            assertTrue(HubServiceApp.findHubById(List.of(), "H-500").isEmpty());
        }
    }

    
    // GET /hubs/{hubId} - full endpoint, against a real Javalin instance
    // wired to a local stub standing in for ingestion-service
    @Nested
    @DisplayName("GET /hubs/{hubId} (integration)")
    class HubsEndpointTests {

        private HttpServer stubIngestion;
        private Javalin app;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private String startStubIngestion(int statusCode, String body) throws IOException {
            stubIngestion = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stubIngestion.createContext("/hubs", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            stubIngestion.start();
            return "http://localhost:" + stubIngestion.getAddress().getPort();
        }

        @AfterEach
        void tearDown() {
            if (app != null) app.stop();
            if (stubIngestion != null) stubIngestion.stop(0);
        }

        @Test
        void returnsHubDetailsForKnownId() throws Exception {
            String ingestionUrl = startStubIngestion(200,
                    "[{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}]");
            app = HubServiceApp.createApp(ingestionUrl).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/hubs/H-500")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Johannesburg Central"));
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            String ingestionUrl = startStubIngestion(200,
                    "[{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}]");
            app = HubServiceApp.createApp(ingestionUrl).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/hubs/H-999")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
        }

        @Test
        void returns502WhenIngestionServiceIsUnreachable() throws Exception {
            app = HubServiceApp.createApp("http://localhost:1").start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/hubs/H-500")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(502, response.statusCode());
        }

        @Test
        void returns502WhenIngestionServiceReturnsAnErrorStatus() throws Exception {
            String ingestionUrl = startStubIngestion(500, "internal error");
            app = HubServiceApp.createApp(ingestionUrl).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/hubs/H-500")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(502, response.statusCode());
        }
    }
}