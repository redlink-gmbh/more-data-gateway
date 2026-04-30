package io.redlink.more.data.service.observations.limesurvey;

import io.redlink.more.data.model.CallbackResult;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.util.DateTimeUtils;
import io.redlink.more.data.util.MapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LimeSurveyComponent implements ObservationComponent {
    private static final Logger LOG = LoggerFactory.getLogger(LimeSurveyComponent.class);
    private static final String LIME_SURVEY_ID_KEY = "limeSurveyId";
    private static final String LIME_SURVEY_TOKEN_KEY = "token";
    private static final String LIME_SURVEY_URL_KEY = "limeUrl";

    private static final String[] LIME_SURVEY_ID_RESPONSE_KEYS = {"surveyId", "survey-id", "surveyid"};
    private static final String[] LIME_SURVEY_SAVE_ID_KEYS = {"savedId", "savedid", "saveId"};
    private static final String[] OBSERVATION_ID_KEYS = {"observationId", "observation-id", "observationid"};
    private static final String[] STUDY_ID_KEYS = {"studyId", "study-id", "studyid"};

    private final LimeSurveyRequestService limeSurveyRequestService;
    private final ElasticService elasticService;
    private final StudyService studyService;

    public LimeSurveyComponent(LimeSurveyRequestService limeSurveyRequestService, ElasticService elasticService, StudyService studyService) {
        this.limeSurveyRequestService = limeSurveyRequestService;
        this.elasticService = elasticService;
        this.studyService = studyService;
    }

    @Override
    public String getObservationType() {
        return "lime-survey-observation";
    }

    @Override
    public Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd) {
        return generateLimeSurveyUrl(observation);
    }

    @Override
    public boolean necessaryCallbackParameters(Map<String, String> parameters) {
        return MapperUtils.containsParameter(parameters, OBSERVATION_ID_KEYS)
                && MapperUtils.containsParameter(parameters, STUDY_ID_KEYS)
                && MapperUtils.containsParameter(parameters, LIME_SURVEY_TOKEN_KEY)
                && MapperUtils.containsParameter(parameters, LIME_SURVEY_ID_RESPONSE_KEYS)
                && MapperUtils.containsParameter(parameters, LIME_SURVEY_SAVE_ID_KEYS);
    }

    @Override
    public Optional<CallbackResult> processCallback(Map<String, String> parameters) {
        Optional<Integer> observationId = Optional.ofNullable(MapperUtils.getParameter(parameters, OBSERVATION_ID_KEYS))
                .map(Integer::parseInt);
        Optional<Long> studyIdParam = Optional.ofNullable(MapperUtils.getParameter(parameters, STUDY_ID_KEYS))
                .map(Long::parseLong);
        String token = MapperUtils.getParameter(parameters, LIME_SURVEY_TOKEN_KEY);
        Optional<Integer> savedId = Optional.ofNullable(MapperUtils.getParameter(parameters, LIME_SURVEY_SAVE_ID_KEYS))
                .map(Integer::parseInt);
        Optional<Integer> surveyId = Optional.ofNullable(MapperUtils.getParameter(parameters, LIME_SURVEY_ID_RESPONSE_KEYS))
                .map(Integer::parseInt);

        if (observationId.isEmpty() || studyIdParam.isEmpty() || token == null || savedId.isEmpty() || surveyId.isEmpty()) {
            LOG.warn("Missing parameters for LimeSurvey Component: observationId={}, studyId={}, token={}, savedId={}, surveyId={}", observationId, studyIdParam, token, savedId, surveyId);
            throw new IllegalArgumentException("Necessary parameter not provided! Please provide all of these: studyID, observationId, token!");
        }

        RoutingInfo routingInfo = studyService.getRoutingInfoByToken(studyIdParam.get(), observationId.get(), token)
                .orElseThrow(() -> {
                    LOG.warn("Could not find RoutingInfo for LimeSurvey Component: observationId={}, studyId={}, token={}, savedId={}, surveyId={}", observationId, studyIdParam, token, savedId, surveyId);
                    return new IllegalArgumentException("Could not find RoutingInfo for LimeSurvey Component for provided parameters");
                });

        if (storeAnswer(surveyId.get(), savedId.get(), token, routingInfo, Integer.toString(observationId.get()))) {
            LOG.debug("Stored LimeSurvey answer for survey {}, token {}, observation {}", surveyId.get(), token, observationId.get());
            return Optional.of(new CallbackResult(routingInfo, observationId.get()));
        }
        LOG.warn("Failed to store LimeSurvey answer for survey {}, token {}, observation {}", surveyId.get(), token, observationId.get());
        return Optional.empty();
    }

    private Optional<String> generateLimeSurveyUrl(Observation observation) {
        var props = observation.properties();
        if (!(props instanceof Map<?, ?> properties)) {
            LOG.warn("Observation properties are not a Map! {}", props);
            return Optional.empty();
        }

        String surveyId = asString(properties.get(LIME_SURVEY_ID_KEY));
        String surveyToken = asString(properties.get(LIME_SURVEY_TOKEN_KEY));
        String surveyUrl = limeSurveyRequestService.getBaseUrl()
                .orElse(asString(properties.get(LIME_SURVEY_URL_KEY)));

        return formatLimeSurveyDeepLink(surveyId, surveyToken, surveyUrl);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Optional<String> formatLimeSurveyDeepLink(String surveyId, String surveyToken, String surveyUrl) {
        if (surveyId == null || surveyId.isBlank() || surveyToken == null || surveyToken.isBlank() || surveyUrl == null || surveyUrl.isBlank()) {
            throw new IllegalStateException("Lime survey token, ID or url is empty!");
        }

        String base = surveyUrl.startsWith("http") ? surveyUrl : "https://" + surveyUrl;
        String normalizedBase = base.endsWith("/") ? base : base + "/";
        String encodedToken = URLEncoder.encode(surveyToken, StandardCharsets.UTF_8);

        try {
            URI baseUri = URI.create(normalizedBase);
            URI result = new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    baseUri.getPath() + surveyId,
                    LIME_SURVEY_TOKEN_KEY + "=" + encodedToken,
                    null
            );
            return Optional.of(result.toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    String.format("Could not create Uri for lime survey %s, with survey id %s", surveyUrl, surveyId),
                    e
            );
        }
    }

    private boolean storeAnswer(Integer surveyId, Integer saveId, String token, RoutingInfo routingInfo, String observationId) {
        Optional<Map<String, Object>> answerOpt = limeSurveyRequestService.getAnswer(token, surveyId, saveId);
        if (answerOpt.isPresent() && !answerOpt.get().isEmpty()) {
            LOG.info("Received answer for LimeSurvey survey {}, routingInfo {}, savedId {}", surveyId, routingInfo, saveId);
            Map<String, Object> answer = answerOpt.get();
            Object submitDateObj = answer.remove("submitdate");
            Instant dateSubmitted = Optional
                    .ofNullable(submitDateObj)
                    .map(Object::toString)
                    .map(obj -> DateTimeUtils.parseInstantWithOffset(obj, ZoneOffset.UTC))
                    .orElse(Instant.now());
            DataPoint dataPoint = new DataPoint(
                    UUID.randomUUID().toString(),
                    observationId,
                    getObservationType(),
                    getObservationType(),
                    Instant.now(),
                    dateSubmitted,
                    answer
            );

            try {
                elasticService.storeDataPoints(List.of(dataPoint), routingInfo);
                LOG.info("Stored LimeSurvey answer for survey {}, token {}, observation {}", surveyId, token, observationId);
                return true;
            } catch (IOException e) {
                LOG.error("Error storing LimeSurvey answers: {}", e.toString());
            }
        } else {
            LOG.warn("Could not fetch answer for LimeSurvey survey {}, token {}, savedId {}, routingInfo {}", surveyId, token, saveId, routingInfo);
        }
        return false;
    }
}
