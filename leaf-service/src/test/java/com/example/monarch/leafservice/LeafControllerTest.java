package com.example.monarch.leafservice;

import com.example.monarch.common.types.Metric;
import com.example.monarch.common.types.Target;
import com.example.monarch.common.types.TimeSeries;
import com.example.monarch.common.types.TimeSeriesPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeafControllerTest {

    private LeafController leafController;

    @Mock
    private KafkaTemplate<String, TimeSeriesPoint> kafkaTemplate;

    @BeforeEach
    void setUp() {
        leafController = new LeafController(null, null, null, kafkaTemplate); // Pass null for WebClient dependencies
        // Manually set leafId for testing purposes as @PostConstruct is not called in unit tests
        ReflectionTestUtils.setField(leafController, "leafId", 1);
    }

    // @Test
    // void writeTimeSeries_storesDataAndSendsToKafka() {
    //     Target target = Target.builder().schemaName("TestTarget").fields(Map.of("key1", "value1")).build();
    //     Metric metric = Metric.builder().schemaName("TestMetric").fields(Map.of("key2", "value2")).build();
    //     TimeSeriesPoint point1 = TimeSeriesPoint.builder().timestamp(100L).value(10.0).build();
    //     TimeSeriesPoint point2 = TimeSeriesPoint.builder().timestamp(200L).value(20.0).build();
    //     TimeSeries timeSeries = TimeSeries.builder().target(target).metric(metric).points(Arrays.asList(point1, point2)).build();

    //     leafController.writeTimeSeries(timeSeries).block();

    //     // Verify data is stored
    //     Map<String, TimeSeries> timeSeriesStore = (Map<String, TimeSeries>) ReflectionTestUtils.getField(leafController, "timeSeriesStore");
    //     assertNotNull(timeSeriesStore);
    //     String expectedKey = "TestTarget_value1_TestMetric"; // Simplified key generation
    //     assertEquals(1, timeSeriesStore.size());
    //     assertEquals(timeSeries, timeSeriesStore.get(expectedKey));

    //     // Verify Kafka messages are sent
    //     verify(kafkaTemplate).send(eq("monarch-recovery-logs"), eq(expectedKey), eq(point1));
    //     verify(kafkaTemplate).send(eq("monarch-recovery-logs"), eq(expectedKey), eq(point2));

    //     // No WebClient interactions to verify in this simplified unit test
    // }

    // @Test
    // void getData_retrievesFilteredData() {
    //     // Prepare some data in the store
    //     Target target1 = Target.builder().schemaName("Target1").fields(Map.of("k1", "v1")).build();
    //     Metric metric1 = Metric.builder().schemaName("/metric/one").fields(Map.of("m1", "mv1")).build();
    //     TimeSeriesPoint p1 = TimeSeriesPoint.builder().timestamp(100L).value(1.0).build();
    //     TimeSeriesPoint p2 = TimeSeriesPoint.builder().timestamp(150L).value(2.0).build();
    //     TimeSeriesPoint p3 = TimeSeriesPoint.builder().timestamp(200L).value(3.0).build();
    //     TimeSeries ts1 = TimeSeries.builder().target(target1).metric(metric1).points(Arrays.asList(p1, p2, p3)).build();
    //     ReflectionTestUtils.invokeMethod(leafController, "writeTimeSeries", ts1);

    //     Target target2 = Target.builder().schemaName("Target2").fields(Map.of("k2", "v2")).build();
    //     Metric metric2 = Metric.builder().schemaName("/metric/two").fields(Map.of("m2", "mv2")).build();
    //     TimeSeriesPoint p4 = TimeSeriesPoint.builder().timestamp(120L).value(4.0).build();
    //     TimeSeriesPoint p5 = TimeSeriesPoint.builder().timestamp(220L).value(5.0).build();
    //     TimeSeries ts2 = TimeSeries.builder().target(target2).metric(metric2).points(Arrays.asList(p4, p5)).build();
    //     ReflectionTestUtils.invokeMethod(leafController, "writeTimeSeries", ts2);

    //     // Test retrieval for /metric/one within a time range
    //     List<TimeSeriesPoint> result = leafController.getData("/metric/one", 120L, 200L).block();
    //     assertNotNull(result);
    //     assertEquals(2, result.size());
    //     assertEquals(p2, result.get(0));
    //     assertEquals(p3, result.get(1));

    //     // Test retrieval for /metric/two within a time range
    //     result = leafController.getData("/metric/two", 100L, 200L).block();
    //     assertNotNull(result);
    //     assertEquals(1, result.size());
    //     assertEquals(p4, result.get(0));
    // }
}

