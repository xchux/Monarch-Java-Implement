package com.example.monarch.recoveryconsumer;

import com.example.monarch.common.types.TimeSeriesPoint;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RecoveryLogConsumer {

    @KafkaListener(topics = "monarch-recovery-logs", groupId = "recovery-group")
    public void listen(String key, TimeSeriesPoint point) {
        System.out.println("Received recovery log - Key: " + key + ", Point: " + point);
        // In a real scenario, this would involve persisting the data or replaying it to a Leaf
    }
}
