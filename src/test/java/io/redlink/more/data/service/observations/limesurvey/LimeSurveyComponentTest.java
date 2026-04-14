package io.redlink.more.data.service.observations.limesurvey;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimeSurveyComponentTest {

    @Mock
    private LimeSurveyRequestService limeSurveyRequestService;

    @Mock
    private ElasticService elasticService;

    private LimeSurveyComponent limeSurveyComponent;

    @BeforeEach
    void setUp() {
        limeSurveyComponent = new LimeSurveyComponent(limeSurveyRequestService, elasticService);
    }

    @Test
    void testGetObservationType() {
        assertEquals("lime-survey-observation", limeSurveyComponent.getObservationType());
    }

    @Test
    void testProduceUrl() {
        Map<String, Object> properties = Map.of(
                "limeSurveyId", "123",
                "token", "abc",
                "limeUrl", "http://limesurvey.example.com"
        );
        Observation observation = new Observation(1, 1, "Title", "lime-survey-observation", "Info", properties, null, Instant.now(), Instant.now(), false, false, false, Set.of());

        Optional<String> url = limeSurveyComponent.produceUrl(observation, null, null, null);

        assertTrue(url.isPresent());
        assertTrue(url.get().contains("123"));
        assertTrue(url.get().contains("token=abc"));
    }

    @Test
    void testProcessCallbackSuccess() throws Exception {
        Map<String, String> parameters = Map.of(
                "token", "token123",
                "saveId", "50",
                "surveyId", "100",
                "limeSurveyId", "token123" // The code uses LIME_SURVEY_ID_KEY which is "limeSurveyId" for the token in storeAnswer
        );
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        Observation observation = new Observation(1, 1, "Title", "lime-survey-observation", "Info", Map.of(), null, Instant.now(), Instant.now(), false, false, false, Set.of());

        when(limeSurveyRequestService.getAnswer("token123", 100, 50))
                .thenReturn(Optional.of(new java.util.HashMap<>(Map.of("some_key", "some_value"))));

        boolean result = limeSurveyComponent.processCallback(parameters, routingInfo, observation, Instant.now(), Instant.now());

        assertTrue(result);
        verify(elasticService).storeDataPoints(anyList(), eq(routingInfo));
    }

    @Test
    void testProcessCallbackMissingParams() {
        Map<String, String> parameters = Map.of("token", "token123");
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        Observation observation = new Observation(1, 1, "Title", "lime-survey-observation", "Info", Map.of(), null, Instant.now(), Instant.now(), false, false, false, Set.of());

        assertThrows(IllegalArgumentException.class, () -> limeSurveyComponent.processCallback(parameters, routingInfo, observation, Instant.now(), Instant.now()));
    }
}
