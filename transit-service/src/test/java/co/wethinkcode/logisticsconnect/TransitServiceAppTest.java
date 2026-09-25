package co.wethinkcode.logisticsconnect;

import com.sun.net.httpserver.HttpServer;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class TransitServiceAppTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);

    private HttpServer hubStub;
    private HttpServer delayStub;
    private Javalin app;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (app != null) app.stop();
        if (hubStub != null) hubStub.stop(0);
        if (delayStub != null) delayStub.stop(0);
    }

    private String startStub(HttpServer server, String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        return "http://localhost:" + server.getAddress().getPort();
    }

    private HttpServer newServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        return server;
    }

    @Test
    void returnsEtaWhenHubAndDelayStageBothFound() throws Exception {
        hubStub = newServer();
        String hubUrl = startStub(hubStub, "/hubs/H-500", 200,
                "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

        delayStub = newServer();
        String delayUrl = startStub(delayStub, "/delay-stage/H-500", 200, "{\"hubId\":\"H-500\",\"stage\":3}");

        app = TransitServiceApp.createApp(hubUrl, delayUrl, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"hubId\":\"H-500\""));
        assertTrue(response.body().contains("\"delayStage\":3"));
        assertTrue(response.body().contains("2026-09-25T18:00:00Z")); // earliest: 24h + 3*6h
    }

    @Test
    void missingDelayStageDefaultsToZeroRatherThanFailing() throws Exception {
        hubStub = newServer();
        String hubUrl = startStub(hubStub, "/hubs/H-500", 200,
                "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

        delayStub = newServer();
        String delayUrl = startStub(delayStub, "/delay-stage/H-500", 404, "{\"error\":\"No delay stage recorded for hub H-500\"}");

        app = TransitServiceApp.createApp(hubUrl, delayUrl, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"delayStage\":0"));
    }

    @Test
    void unknownHubReturns404() throws Exception {
        hubStub = newServer();
        String hubUrl = startStub(hubStub, "/hubs/H-999", 404, "{\"error\":\"No hub found with id H-999\"}");

        delayStub = newServer();
        // delay-stage-service isn't even reached in this case, but stub it anyway to keep the test isolated
        String delayUrl = startStub(delayStub, "/delay-stage/H-999", 404, "{}");

        app = TransitServiceApp.createApp(hubUrl, delayUrl, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-999")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void hubServiceUnreachableReturns502() throws Exception {
        delayStub = newServer();
        String delayUrl = startStub(delayStub, "/delay-stage/H-500", 200, "{\"hubId\":\"H-500\",\"stage\":3}");

        app = TransitServiceApp.createApp("http://localhost:1", delayUrl, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode());
    }

    @Test
    void delayStageServiceUnreachableReturns502() throws Exception {
        hubStub = newServer();
        String hubUrl = startStub(hubStub, "/hubs/H-500", 200,
                "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

        app = TransitServiceApp.createApp(hubUrl, "http://localhost:1", FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode());
    }
}