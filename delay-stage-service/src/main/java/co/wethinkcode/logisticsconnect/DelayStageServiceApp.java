package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns) per hub.
 *
 * <p>Standalone service - per the integration contract, delay-stage-service
 * doesn't call hub-service or ingestion-service. It accepts any {@code hubId}
 * string with no cross-service check that the hub actually exists.
 *
 * <p>{@code POST /delay-stage/{hubId}} is also where the stage-3 MQ publish
 * to {@code package-status-topic} will eventually happen (see
 * {@code co.wethinkcode.logisticsconnect.mq.MqConfig}) - not wired in yet
 * at this stage.
 */
public class DelayStageServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        createApp(new DelayStageStore()).start(7052);
    }

    /**
     * Builds (but does not start) the Javalin app around the given store.
     * Split from {@link #main} so tests can use an isolated, fresh store
     * per test rather than sharing state.
     *
     * @param store the delay-stage state to read from and write to
     * @return the configured, unstarted Javalin app
     */
    static Javalin createApp(DelayStageStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            Optional<Integer> stage = store.getStage(hubId);

            if (stage.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND)
                   .json(Map.of("error", "No delay stage recorded for hub " + hubId));
                return;
            }

            ctx.json(Map.of("hubId", hubId, "stage", stage.get()));
        });

        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            int stage;
            try {
                stage = parseStage(ctx.body());
            } catch (IllegalArgumentException e) {
                ctx.status(HttpStatus.BAD_REQUEST)
                   .json(Map.of("error", e.getMessage()));
                return;
            }

            store.setStage(hubId, stage);
            ctx.json(Map.of("hubId", hubId, "stage", stage));

            // MQ TODO (stage 3): publish { hubId, stage, timestamp } to
            // MqConfig.TOPIC here, replacing transit-service's direct
            // GET call to this endpoint with a subscription instead.
        });

        return app;
    }

    /**
     * Parses and validates a {@code POST /delay-stage/{hubId}} request
     * body of the form {@code {"stage": 3}}. Pure - no I/O - so it's
     * unit-testable directly against a raw JSON string.
     *
     * @param json raw request body
     * @return the validated stage value (0-8 inclusive)
     * @throws IllegalArgumentException if the body isn't valid JSON, is
     *                                   missing the {@code stage} field,
     *                                   the field isn't an integer, or the
     *                                   value is outside 0-8
     */
    static int parseStage(String json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Request body is not valid JSON");
        }

        JsonNode stageNode = node.get("stage");
        if (stageNode == null || stageNode.isNull()) {
            throw new IllegalArgumentException("Missing required field: stage");
        }
        if (!stageNode.isInt()) {
            throw new IllegalArgumentException("Field 'stage' must be an integer");
        }

        int stage = stageNode.asInt();
        if (stage < 0 || stage > 8) {
            throw new IllegalArgumentException("Field 'stage' must be between 0 and 8 (got " + stage + ")");
        }

        return stage;
    }
}