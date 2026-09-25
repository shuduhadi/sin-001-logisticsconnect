package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calculates estimated arrival windows based on hub and delay stage.
 *
 * <p>On each request to {@code GET /transit/{hubId}}, calls hub-service and
 * delay-stage-service synchronously (see {@link HubClient} and
 * {@link DelayStageClient}), then combines the results via
 * {@link EtaCalculator}.
 *
 * <p>MQ TODO (stage 3): subscribe to {@code package-status-topic} (see
 * {@code co.wethinkcode.logisticsconnect.mq.MqConfig}) instead of calling
 * delay-stage-service directly.
 */
public class TransitServiceApp {

    static final String DEFAULT_HUB_SERVICE_URL = "http://localhost:7051";
    static final String DEFAULT_DELAY_STAGE_SERVICE_URL = "http://localhost:7052";

    public static void main(String[] args) {
        createApp(DEFAULT_HUB_SERVICE_URL, DEFAULT_DELAY_STAGE_SERVICE_URL, Clock.systemUTC()).start(7053);
    }

    /**
     * Builds (but does not start) the Javalin app. Split from {@link #main}
     * so tests can point it at stub servers and use a fixed clock.
     *
     * @param hubServiceUrl        base URL of hub-service
     * @param delayStageServiceUrl base URL of delay-stage-service
     * @param clock                source of "now" for ETA calculation
     * @return the configured, unstarted Javalin app
     */
    static Javalin createApp(String hubServiceUrl, String delayStageServiceUrl, Clock clock) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/transit/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            Optional<HubRecord> hub;
            try {
                hub = HubClient.fetchHub(hubServiceUrl, hubId);
            } catch (IOException | InterruptedException e) {
                ctx.status(HttpStatus.BAD_GATEWAY)
                   .json(Map.of("error", "hub-service is unavailable: " + e.getMessage()));
                return;
            }

            if (hub.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND)
                   .json(Map.of("error", "No hub found with id " + hubId));
                return;
            }

            int delayStage;
            try {
                // Missing delay-stage data (404) means "no known delay yet",
                // not an error - default to stage 0 rather than failing the request.
                delayStage = DelayStageClient.fetchStage(delayStageServiceUrl, hubId).orElse(0);
            } catch (IOException | InterruptedException e) {
                ctx.status(HttpStatus.BAD_GATEWAY)
                   .json(Map.of("error", "delay-stage-service is unavailable: " + e.getMessage()));
                return;
            }

            EtaWindow eta = EtaCalculator.calculateEta(delayStage, clock);

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("hubId", hub.get().getHubId());
            responseBody.put("sortingCenter", hub.get().getSortingCenter());
            responseBody.put("province", hub.get().getProvince());
            responseBody.put("delayStage", delayStage);
            responseBody.put("estimatedArrival", Map.of(
                    "earliest", eta.earliest().toString(),
                    "latest", eta.latest().toString()
            ));

            ctx.json(responseBody);
        });

        return app;
    }
}