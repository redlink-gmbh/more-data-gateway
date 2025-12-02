/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.api.StorageService;
import io.redlink.more.data.elastic.model.ElasticDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ElasticService implements StorageService {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticService.class);

    private final ElasticsearchClient client;

    ElasticService(ElasticsearchClient elasticsearchClient) {
        this.client = elasticsearchClient;
    }

    private String getElasticIndexName(RoutingInfo routingInfo) {
        return "study_" + routingInfo.studyId();
    }


    public List<String> storeDataPoints(final List<DataPoint> dataBulk, final RoutingInfo routingInfo) {
        final String indexName = getElasticIndexName(routingInfo);
        final String uidPrefix = generateUidPrefix(routingInfo);

        try {
            deleteGarminDataPoints(dataBulk, routingInfo);
            final BulkRequest.Builder br = new BulkRequest.Builder()
                    .index(indexName);

            for (DataPoint dataPoint : dataBulk) {
                final var uid = uidPrefix + dataPoint.datapointId();
                final ElasticDataPoint elasticDoc = ElasticDataPoint.toElastic(dataPoint, routingInfo);
                br.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(uid)
                                .document(elasticDoc)
                        )
                );
            }

            LOG.debug("Sending {} data-points to {}", dataBulk.size(), indexName);
            final BulkResponse result = client.bulk(br.build());

            // Log errors, if any
            if (LOG.isErrorEnabled() && result.errors()) {
                LOG.error("Bulk had errors");
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        LOG.error("{}: {}", item.id(), item.error().reason());
                    }
                }
            }

            return result.items().stream()
                    .filter(i -> i.error() == null)
                    .map(BulkResponseItem::id)
                    .filter(StringUtils::isNotBlank)
                    .map(i -> i.substring(uidPrefix.length()))
                    .toList();
        } catch (IOException | ElasticsearchException e) {
            LOG.warn("Error when sending data bulk to elastic index. Error message: ", e);
            return List.of();
        }
    }

    private void deleteGarminDataPoints(final List<DataPoint> dataBulk, final RoutingInfo routingInfo) {
        Set<String> garminSummaryIdDataBulk = dataBulk.stream()
                .filter(dp -> dp.data().containsKey(GarminTimeData.GARMIN_SUMMARY_ID_KEY))
                .map(dp -> (String) dp.data().get(GarminTimeData.GARMIN_SUMMARY_ID_KEY))
                .collect(Collectors.toSet());
        deleteDataPointsByGarminSummaryIds(routingInfo.studyId(), routingInfo.participantId(), garminSummaryIdDataBulk);
    }

    // Deletes all datapoints including the garmin summary id
    private void deleteDataPointsByGarminSummaryIds(final long studyId,
                                                    final int participantId,
                                                    final Set<String> summaryIds) {
        if (summaryIds.isEmpty()) {
            return;
        }

        final String indexName = "study_" + studyId;
        final String summaryField = "data_" + GarminTimeData.GARMIN_SUMMARY_ID_KEY + ".keyword";

        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                    .index(indexName)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("study_id")
                                            .value("study_" + studyId)))
                                    .must(m -> m.term(t -> t.field("participant_id")
                                            .value("participant_" + participantId)))
                                    .must(m -> m.bool(bb -> bb
                                            .should(s -> s.terms(t -> t
                                                    .field(summaryField)
                                                    .terms(v -> v.value(
                                                            summaryIds.stream()
                                                                    .map(FieldValue::of)
                                                                    .toList()
                                                    ))
                                            ))
                                            .minimumShouldMatch("1")
                                    ))
                            )
                    )
                    .build();

            DeleteByQueryResponse response = client.deleteByQuery(request);
            long deleted = response.deleted() == null ? 0L : response.deleted();
            LOG.info("Deleted {} datapoints from index {} for study_id={}, participant_id={}, summary_ids={}",
                    deleted, indexName, studyId, participantId, summaryIds);
        } catch (IOException | ElasticsearchException e) {
            LOG.warn("Error when deleting datapoints by attributes from elastic index. study_id={}, participant_id={}, summary_ids={}",
                    studyId, participantId, summaryIds, e);
        }
    }


    private String generateUidPrefix(RoutingInfo routingInfo) {
        return String.format("%s_%s_", routingInfo.studyId(), routingInfo.participantId());
    }
}
