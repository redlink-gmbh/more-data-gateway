package io.redlink.more.data.service.polar;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.polar.PolarDataType;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.polar.AbstractPolarTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolarDataTransformationServiceTest {

    @Mock
    private StudyRepository studyRepository;

    private PolarDataTransformationService service;

    private final RoutingInfo routingInfo = new RoutingInfo(1L, 10, 1, Set.of(), true, true);

    private final Instant participantStart = Instant.parse("2024-01-01T00:00:00Z");
    private final Instant participantEnd   = Instant.parse("2024-12-31T23:59:59Z");

    /** Minimal concrete transformer that produces one DataPoint per raw item for HR type. */
    private static class StubHrTransformer extends AbstractPolarTransformer {

        @Override
        public PolarDataType getSupportedType() {
            return PolarDataType.HR;
        }

        @Override
        public String getObservationType() {
            return "polar-hr-observation";
        }

        @Override
        public List<DataPoint> transform(
                List<Observation> observations,
                Map<String, Object> rawDataItem,
                Instant participantStart,
                Instant participantEnd
        ) {
            return List.of(new DataPoint(
                    "dp-" + rawDataItem.getOrDefault("id", "x"),
                    "obs-1",
                    getObservationType(),
                    getSupportedType().name(),
                    Instant.now(),
                    Instant.now(),
                    Map.of("hr", rawDataItem.getOrDefault("hr", 0))
            ));
        }
    }

    @BeforeEach
    void setUp() {
        service = new PolarDataTransformationService(studyRepository, List.of(new StubHrTransformer()));
    }

    // -----------------------------------------------------------------------
    // Guard: empty / null raw data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transformData: returns empty map when rawDataItems is null")
    void returnsEmpty_whenRawDataNull() {
        Map<RoutingInfo, List<DataPoint>> result = service.transformData(PolarDataType.HR, routingInfo, null);
        assertThat(result).isEmpty();
        verify(studyRepository, never()).findParticipant(any());
    }

    @Test
    @DisplayName("transformData: returns empty map when rawDataItems is empty")
    void returnsEmpty_whenRawDataEmpty() {
        Map<RoutingInfo, List<DataPoint>> result = service.transformData(PolarDataType.HR, routingInfo, Collections.emptyList());
        assertThat(result).isEmpty();
        verify(studyRepository, never()).findParticipant(any());
    }

    // -----------------------------------------------------------------------
    // Guard: no transformer registered for type
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transformData: returns empty map when no transformer registered for data type")
    void returnsEmpty_whenNoTransformerForType() {
        Map<RoutingInfo, List<DataPoint>> result = service.transformData(
                PolarDataType.ACC,  // no transformer registered for ACC
                routingInfo,
                List.of(Map.of("id", "1"))
        );
        assertThat(result).isEmpty();
        verify(studyRepository, never()).findParticipant(any());
    }

    // -----------------------------------------------------------------------
    // Guard: participant not found
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transformData: returns empty map when participant not found")
    void returnsEmpty_whenParticipantNotFound() {
        given(studyRepository.findParticipant(any(RoutingInfo.class))).willReturn(Optional.empty());

        Map<RoutingInfo, List<DataPoint>> result = service.transformData(
                PolarDataType.HR, routingInfo, List.of(Map.of("id", "1", "hr", 70))
        );

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Guard: participant start after end
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transformData: returns empty map when participant start is after end")
    void returnsEmpty_whenParticipantStartAfterEnd() {
        SimpleParticipant invalid = new SimpleParticipant(
                10, "alias",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z")  // end before start
        );
        given(studyRepository.findParticipant(any(RoutingInfo.class))).willReturn(Optional.of(invalid));

        Map<RoutingInfo, List<DataPoint>> result = service.transformData(
                PolarDataType.HR, routingInfo, List.of(Map.of("id", "1", "hr", 70))
        );

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transformData: produces DataPoints and returns them keyed by RoutingInfo")
    void happyPath_producesDataPoints() {
        SimpleParticipant participant = new SimpleParticipant(10, "alias", participantStart, participantEnd);
        given(studyRepository.findParticipant(any(RoutingInfo.class))).willReturn(Optional.of(participant));
        given(studyRepository.getObservationsByTypes(any(), any(), any())).willReturn(List.of());

        List<Map<String, Object>> rawItems = List.of(
                Map.of("id", "a", "hr", 70),
                Map.of("id", "b", "hr", 80)
        );

        Map<RoutingInfo, List<DataPoint>> result = service.transformData(PolarDataType.HR, routingInfo, rawItems);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey(routingInfo);
        List<DataPoint> dataPoints = result.get(routingInfo);
        assertThat(dataPoints).hasSize(2);
        assertThat(dataPoints).allSatisfy(dp -> {
            assertThat(dp.observationType()).isEqualTo("polar-hr-observation");
            assertThat(dp.dataType()).isEqualTo("HR");
            assertThat(dp.data()).containsKey("hr");
        });
    }

    @Test
    @DisplayName("transformData: returns empty map when transformer produces no DataPoints")
    void returnsEmpty_whenTransformerProducesNothing() {
        // Transformer that always returns empty
        AbstractPolarTransformer emptyTransformer = new AbstractPolarTransformer() {
            @Override public PolarDataType getSupportedType() { return PolarDataType.HR; }
            @Override public String getObservationType() { return "polar-hr-observation"; }
            @Override public List<DataPoint> transform(List<Observation> obs, Map<String, Object> raw, Instant start, Instant end) {
                return Collections.emptyList();
            }
        };

        PolarDataTransformationService svc = new PolarDataTransformationService(studyRepository, List.of(emptyTransformer));

        SimpleParticipant participant = new SimpleParticipant(10, "alias", participantStart, participantEnd);
        given(studyRepository.findParticipant(any(RoutingInfo.class))).willReturn(Optional.of(participant));
        given(studyRepository.getObservationsByTypes(any(), any(), any())).willReturn(List.of());

        Map<RoutingInfo, List<DataPoint>> result = svc.transformData(
                PolarDataType.HR, routingInfo, List.of(Map.of("id", "x"))
        );

        assertThat(result).isEmpty();
    }
}
