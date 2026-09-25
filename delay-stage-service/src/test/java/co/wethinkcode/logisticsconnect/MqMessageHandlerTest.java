package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MqMessageHandlerTest {

    @Test
    void updatesTheCacheFromAValidMessage() throws Exception {
        DelayStageCache cache = new DelayStageCache();

        MqMessageHandler.handleMessage("{\"hubId\":\"H-501\",\"stage\":5,\"timestamp\":\"2026-09-25T10:00:00Z\"}", cache);

        assertEquals(Optional.of(5), cache.getStage("H-501"));
    }

    @Test
    void laterMessageForSameHubOverwritesEarlierOne() throws Exception {
        DelayStageCache cache = new DelayStageCache();

        MqMessageHandler.handleMessage("{\"hubId\":\"H-501\",\"stage\":3}", cache);
        MqMessageHandler.handleMessage("{\"hubId\":\"H-501\",\"stage\":7}", cache);

        assertEquals(Optional.of(7), cache.getStage("H-501"));
    }

    @Test
    void malformedJsonThrows() {
        DelayStageCache cache = new DelayStageCache();
        assertThrows(Exception.class, () -> MqMessageHandler.handleMessage("not json", cache));
    }
}