package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Synchronous REST client for delay-stage-service's
 * {@code GET /delay-stage/{hubId}} endpoint.
 *
 * <p>Replaced by an MQ subscription in stage 3 - see
 * {@code co.wethinkcode.logisticsconnect.mq.MqConfig}.
 */
public class DelayStageClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Calls {@code GET {baseUrl}/delay-stage/{hubId}} on delay-stage-service.
     *
     * @param baseUrl delay-stage-service base URL, e.g. {@code "http://localhost:7052"}
     * @param hubId   hub identifier
     * @return the current delay stage, or {@link Optional#empty()} if
     *         delay-stage-service returned {@code 404} (no stage recorded yet)
     * @throws IOException          if the request fails, or delay-stage-service
     *                               responds with a status other than {@code 200}/{@code 404}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Optional<Integer> fetchStage(String baseUrl, String hubId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/delay-stage/" + hubId))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("delay-stage-service returned HTTP " + response.statusCode());
        }

        return Optional.of(parseStage(response.body()));
    }

    /**
     * Parses the {@code stage} field out of a delay-stage-service GET
     * response body, e.g. {@code {"hubId":"H-500","stage":3}}. Pure -
     * no network I/O.
     */
    static int parseStage(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json);
        return node.get("stage").asInt();
    }
}