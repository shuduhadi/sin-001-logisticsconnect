package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class DelayStageServiceAppTest {

   
    // parseStage - pure request-body validation, no HTTP
    
    @DisplayName("parseStage")
    class ParseStageTests {

        @Test
        void parsesAValidStagePayload() {
            assertEquals(3, DelayStageServiceApp.parseStage("{\"stage\":3}"));
        }

        @Test
        void acceptsTheBoundaryValuesZeroAndEight() {
            assertEquals(0, DelayStageServiceApp.parseStage("{\"stage\":0}"));
            assertEquals(8, DelayStageServiceApp.parseStage("{\"stage\":8}"));
        }

        @Test
        void missingStageFieldThrows() {
            assertThrows(IllegalArgumentException.class, () -> DelayStageServiceApp.parseStage("{}"));
        }

        @Test
        void nonIntegerStageThrows() {
            assertThrows(IllegalArgumentException.class, () -> DelayStageServiceApp.parseStage("{\"stage\":\"three\"}"));
        }

        @Test
        void negativeStageThrows() {
            assertThrows(IllegalArgumentException.class, () -> DelayStageServiceApp.parseStage("{\"stage\":-1}"));
        }

        @Test
        void stageAboveEightThrows() {
            assertThrows(IllegalArgumentException.class, () -> DelayStageServiceApp.parseStage("{\"stage\":9}"));
        }

        @Test
        void malformedJsonThrows() {
            assertThrows(IllegalArgumentException.class, () -> DelayStageServiceApp.parseStage("not json"));
        }
    }

   
    // GET/POST /delay-stage/{hubId} - full endpoint, real Javalin instance
    @DisplayName("GET/POST /delay-stage/{hubId} (integration)")
    class EndpointTests {

        private Javalin app;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        @AfterEach
        void tearDown() {
            if (app != null) app.stop();
        }

        private String baseUrl() {
            return "http://localhost:" + app.port();
        }

        @Test
        void postThenGetReturnsTheStoredStage() throws Exception {
            app = DelayStageServiceApp.createApp(new DelayStageStore()).start(0);

            HttpResponse<String> postResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-501"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"stage\":3}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, postResponse.statusCode());
            assertTrue(postResponse.body().contains("\"stage\":3"));

            HttpResponse<String> getResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-501")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, getResponse.statusCode());
            assertTrue(getResponse.body().contains("\"hubId\":\"H-501\""));
            assertTrue(getResponse.body().contains("\"stage\":3"));
        }

        @Test
        void getForUnknownHubReturns404() throws Exception {
            app = DelayStageServiceApp.createApp(new DelayStageStore()).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-999")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
        }

        @Test
        void postWithOutOfRangeStageReturns400() throws Exception {
            app = DelayStageServiceApp.createApp(new DelayStageStore()).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-501"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"stage\":9}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
        }

        @Test
        void postWithMalformedBodyReturns400() throws Exception {
            app = DelayStageServiceApp.createApp(new DelayStageStore()).start(0);

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-501"))
                            .POST(HttpRequest.BodyPublishers.ofString("not json"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
        }

        @Test
        void hubIdLookupIsCaseInsensitive() throws Exception {
            app = DelayStageServiceApp.createApp(new DelayStageStore()).start(0);

            httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/h-501"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"stage\":4}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/delay-stage/H-501")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"stage\":4"));
        }
    }
}