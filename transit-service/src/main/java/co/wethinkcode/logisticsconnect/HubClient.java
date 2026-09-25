package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Synchronous REST client for hub-service's {@code GET /hubs/{hubId}}
 * endpoint.
 */
public class HubClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Calls {@code GET {baseUrl}/hubs/{hubId}} on hub-service.
     *
     * @param baseUrl hub-service base URL, e.g. {@code "http://localhost:7051"}
     * @param hubId   hub identifier
     * @return the hub, or {@link Optional#empty()} if hub-service returned {@code 404}
     * @throws IOException          if the request fails, or hub-service responds
     *                               with a status other than {@code 200}/{@code 404}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Optional<HubRecord> fetchHub(String baseUrl, String hubId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/hubs/" + hubId))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("hub-service returned HTTP " + response.statusCode());
        }

        return Optional.of(parseHub(response.body()));
    }

    /**
     * Parses a single hub record, as returned by hub-service's
     * {@code GET /hubs/{hubId}}. Pure - no network I/O.
     */
    static HubRecord parseHub(String json) throws IOException {
        return MAPPER.readValue(json, HubRecord.class);
    }
}