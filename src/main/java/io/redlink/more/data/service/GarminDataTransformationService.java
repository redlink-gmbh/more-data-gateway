package io.redlink.more.data.service;

import io.redlink.more.data.api.app.v1.model.GarminDataPointDTO;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminTimeData;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

@Service
public class GarminDataTransformationService {
    private final StudyRepository studyRepository;

    public GarminDataTransformationService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    public Map<RoutingInfo, List<DataPoint>> transformData(String summaryType, List<ParticipantGarminDataPoint> participantGarminDataPoints) {
        return participantGarminDataPoints
                .stream()
                .map(participantGarminDataPoint -> {
                    var participant = participantGarminDataPoint.participantKeyValue();
                    List<Observation> observations = studyRepository.filterObservations(participant.studyId(), participant.participantId(), observation -> observation.type().contains(GarminService.GARMIN_KEY_TYPE));
                    List<DataPoint> dataPoints = participantGarminDataPoint.garminDataPoints()
                            .parallelStream()
                            .flatMap(data -> observations
                                    .stream()
                                    .flatMap(observation ->
                                            createDataPointFromGarminData(String.valueOf(observation.observationId()), observation.type(), summaryType, data)
                                                    .stream()
                                    )
                            )
                            .toList();
                    return Map.entry(
                            participantKeyValueToRoutingInfo(participant),
                            dataPoints
                    );
                })
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<DataPoint> createDataPointFromGarminData(String observationId, String observationType, String summaryType, GarminDataPointDTO garminDataPointDTO) {
        ArrayList<DataPoint> result = new ArrayList<>();
        Instant unixTimestamp = Instant.ofEpochSecond(garminDataPointDTO.getStartTimeInSeconds());

        ZoneOffset offset = ZoneOffset.ofTotalSeconds(garminDataPointDTO.getStartTimeOffsetInSeconds());

        OffsetDateTime dateTimeWithOffset = unixTimestamp.atOffset(offset);

        if (summaryType.equalsIgnoreCase("dailies") && garminDataPointDTO.getTimeOffsetHeartRateSamples() != null && !garminDataPointDTO.getTimeOffsetHeartRateSamples().isEmpty()) {
            result.addAll(dailyGarminHeartRateSampling(observationId, observationType, dateTimeWithOffset, garminDataPointDTO));
        }

        return result;
    }

    private List<DataPoint> dailyGarminHeartRateSampling(String observationId, String observationType, OffsetDateTime offsetDateTime, GarminDataPointDTO garminDataPointDTO) {
        return extractHrDataWithThreshold(offsetDateTime, garminDataPointDTO.getTimeOffsetHeartRateSamples())
                .stream()
                .map(data -> transformGarminTimeDataToDataPoint(observationId, observationType, garminDataPointDTO.getSummaryId(), DataType.HEARTRATE, data))
                .toList();
    }


    private List<GarminTimeData<Integer>> extractHrDataWithThreshold(OffsetDateTime startDateTime, Map<String, Integer> hrTimeOffset) {
        if (hrTimeOffset == null) {
            return Collections.emptyList();
        }

        return hrTimeOffset
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> new GarminTimeData<>(Instant.ofEpochSecond(startDateTime.toEpochSecond() + Integer.parseInt(entry.getKey())), entry.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private DataPoint transformGarminTimeDataToDataPoint(String observationId, String observationType, String dataId, DataType dataType, GarminTimeData<?> data) {
        return new DataPoint(
                dataId,
                observationId,
                observationType,
                dataType.name(),
                Instant.now(),
                data.timestamp(),
                data.dataToMap(dataType.dataType)
        );
    }

    private RoutingInfo participantKeyValueToRoutingInfo(ParticipantKeyValue participantKeyValue) {
        return new RoutingInfo(participantKeyValue.studyId(), participantKeyValue.participantId(), OptionalInt.empty(), true, true);
    }
}
