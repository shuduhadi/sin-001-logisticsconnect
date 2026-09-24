package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves hub / sorting-center details (place-name source of truth for the
 * LogisticsConnect pipeline).
 *
 * <p>On each request to {@code GET /hubs/{hubId}}, calls ingestion-service's
 * {@code GET /hubs} synchronously to resolve the current cleaned hub data,
 * then looks up the requested ID. No caching at startup — see
 * {@link IngestionClient} for the rationale.
 */
public class HubServiceApp {

    /** Default ingestion-service base URL, used by {@link #main}. */
    static final String DEFAULT_INGESTION_SERVICE_URL = "http://localhost:7050";

    public static void main(String[] args) {
        createApp(DEFAULT_INGESTION_SERVICE_URL).start(7051);
    }

    /**
     * Builds (but does not start) the Javalin app, wired to call
     * ingestion-service at {@code ingestionServiceUrl}. Split from
     * {@link #main} so tests can point it at a stub server instead of the
     * real ingestion-service.
     *
     * @param ingestionServiceUrl base URL of the ingestion-service instance to call
     * @return the configured, unstarted Javalin app
     */
    static Javalin createApp(String ingestionServiceUrl) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            List<HubRecord> hubs;
            try {
                hubs = IngestionClient.fetchHubs(ingestionServiceUrl);
            } catch (IOException | InterruptedException e) {
                ctx.status(HttpStatus.BAD_GATEWAY)
                   .json(Map.of("error", "ingestion-service is unavailable: " + e.getMessage()));
                return;
            }

            Optional<HubRecord> hub = findHubById(hubs, hubId);
            if (hub.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND)
                   .json(Map.of("error", "No hub found with id " + hubId));
                return;
            }

            ctx.json(hub.get());
        });

        return app;
    }

    /**
     * Finds a hub by ID, case-insensitively (so {@code "h-500"} matches
     * {@code "H-500"}). Pure - no network I/O - so it's unit-testable
     * directly against a canned list.
     *
     * @param hubs  hub records to search, as returned by {@link IngestionClient#fetchHubs}
     * @param hubId the ID to look up; may be {@code null}
     * @return the matching hub, or {@link Optional#empty()} if not found
     *         (including when {@code hubId} is {@code null})
     */
    static Optional<HubRecord> findHubById(List<HubRecord> hubs, String hubId) {
        if (hubId == null) return Optional.empty();
        return hubs.stream()
                .filter(h -> h.getHubId() != null && h.getHubId().equalsIgnoreCase(hubId.trim()))
                .findFirst();
    }
}