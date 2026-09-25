package co.wethinkcode.logisticsconnect;

/**
 * Publishes a delay-stage change to interested consumers. Implemented by
 * {@link ActiveMqDelayStagePublisher} for real use, and by simple fakes in
 * tests so the POST endpoint's publish-triggering behavior can be verified
 * without a running broker.
 */
public interface DelayStagePublisher {

    /**
     * Publishes a stage change for a hub.
     *
     * @param hubId normalized hub identifier
     * @param stage the new delay stage (0-8)
     * @throws Exception implementations may throw on any failure (e.g. broker
     *                    unreachable); callers treat this as best-effort and
     *                    must not let a failure here break the REST response
     */
    void publish(String hubId, int stage) throws Exception;
}