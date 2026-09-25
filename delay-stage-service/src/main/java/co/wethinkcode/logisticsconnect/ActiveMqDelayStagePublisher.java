package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes delay-stage changes to the shared {@code package-status-topic}
 * ActiveMQ topic (see {@link MqConfig}).
 *
 * <p>Not unit tested - requires a live broker (see
 * {@code common/docker-compose.yml}). Verified manually: run
 * {@code docker compose up -d} from {@code common/}, then POST a stage
 * change and confirm it lands on the topic via the web console
 * (http://localhost:8161) or a subscribed consumer's logs.
 *
 * <p>Opens and closes a fresh JMS connection per publish call rather than
 * holding one open for the service's lifetime - simpler and adequate for
 * this exercise's request volume, at the cost of some per-call overhead.
 */
public class ActiveMqDelayStagePublisher implements DelayStagePublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void publish(String hubId, int stage) throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageProducer producer = session.createProducer(topic);

            TextMessage message = session.createTextMessage(buildPayload(hubId, stage));
            producer.send(message);
        }
    }

    /** Builds the JSON payload: {"hubId": ..., "stage": ..., "timestamp": ...}. */
    private String buildPayload(String hubId, int stage) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("hubId", hubId);
            payload.put("stage", stage);
            payload.put("timestamp", Instant.now().toString());
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            // Map<String,Object> of primitives/strings can't realistically fail to serialize.
            throw new java.lang.IllegalStateException("Failed to build MQ payload", e);
        }
    }
}