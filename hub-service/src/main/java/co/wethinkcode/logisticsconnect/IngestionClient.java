package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Synchronous REST client for ingestion-service's {@code GET /hubs}
 * endpoint. Used by {@link HubServiceApp} to resolve hub details on each
 * incoming request (stage 2's direct-HTTP-call architecture — stage 3
 * later decouples the delay-stage/transit link, but this one stays a
 * direct call per the integration contract).
 */
public class IngestionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Calls {@code GET {baseUrl}/hubs} on ingestion-service and returns the
     * parsed, cleaned hub records.
     *
     * @param baseUrl ingestion-service base URL, e.g. {@code "http://localhost:7050"}
     * @return the cleaned hub records as returned by ingestion-service
     * @throws IOException          if the request fails, times out, or ingestion-service
     *                               responds with a non-200 status
     * @throws InterruptedException if the calling thread is interrupted while waiting
     *                               for the response
     */
    public static List<HubRecord> fetchHubs(String baseUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/hubs"))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("ingestion-service returned HTTP " + response.statusCode());
        }

        return parseHubs(response.body());
    }

    /**
     * Parses a JSON array of hub records, as returned by ingestion-service's
     * {@code GET /hubs}. Pure - no network I/O - so it's unit-testable
     * against a canned payload.
     *
     * @param json raw JSON array body
     * @return the parsed hub records
     * @throws JsonProcessingException if {@code json} is not a valid array
     *                                  of hub records
     */
    static List<HubRecord> parseHubs(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, new TypeReference<List<HubRecord>>() {});
    }
}