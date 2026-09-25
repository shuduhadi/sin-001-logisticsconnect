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
    private Javalin app;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (app != null) app.stop();
        if (hubStub != null) hubStub.stop(0);
    }

    private String startHubStub(int statusCode, String body) throws IOException {
        hubStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        hubStub.createContext("/hubs/H-500", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        hubStub.start();
        return "http://localhost:" + hubStub.getAddress().getPort();
    }

    @Test
    void returnsEtaUsingStageFromCache() throws Exception {
        String hubUrl = startHubStub(200,
                "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

        DelayStageCache cache = new DelayStageCache();
        cache.updateStage("H-500", 3); // simulates a message already received via MQ

        app = TransitServiceApp.createApp(hubUrl, cache, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"delayStage\":3"));
        assertTrue(response.body().contains("2026-09-25T18:00:00Z")); // earliest: 24h + 3*6h
    }

    @Test
    void noMessageEverReceivedForHubDefaultsToZero() throws Exception {
        String hubUrl = startHubStub(200,
                "{\"hubId\":\"H-500\",\"province\":\"Gauteng\",\"sortingCenter\":\"Johannesburg Central\",\"active\":true,\"mergedFrom\":[]}");

        DelayStageCache cache = new DelayStageCache(); // empty - no message ever arrived

        app = TransitServiceApp.createApp(hubUrl, cache, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"delayStage\":0"));
    }

    @Test
    void unknownHubReturns404() throws Exception {
        String hubUrl = startHubStub(404, "{\"error\":\"No hub found with id H-500\"}");
        DelayStageCache cache = new DelayStageCache();

        app = TransitServiceApp.createApp(hubUrl, cache, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void hubServiceUnreachableReturns502() throws Exception {
        DelayStageCache cache = new DelayStageCache();

        app = TransitServiceApp.createApp("http://localhost:1", cache, FIXED_CLOCK).start(0);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/transit/H-500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode());
    }
}