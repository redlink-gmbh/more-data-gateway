package io.redlink.more.data.repository;

import io.redlink.more.data.model.ObservationAttachment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ObservationAttachmentRepository {

    private static final String GET =
            "SELECT * FROM observation_attachments WHERE study_id = :study_id AND observation_id = :observation_id AND attachment_id = :attachment_id";
    private static final String LIST =
            "SELECT * FROM observation_attachments WHERE study_id = :study_id AND observation_id = :observation_id ORDER BY created";

    private final NamedParameterJdbcTemplate namedTemplate;

    public ObservationAttachmentRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
    }

    public List<ObservationAttachment> listAttachments(long studyId, int observationId) {
        return namedTemplate.query(LIST, new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("observation_id", observationId), ROW_MAPPER);
    }

    public Optional<ObservationAttachment> getAttachment(long studyId, int observationId, UUID attachmentId) {
        try (var stream = namedTemplate.queryForStream(GET, new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("observation_id", observationId)
                .addValue("attachment_id", attachmentId), ROW_MAPPER)) {
            return stream.findFirst();
        }
    }

    private static final RowMapper<ObservationAttachment> ROW_MAPPER = (rs, rowNum) -> new ObservationAttachment(
            rs.getObject("attachment_id", UUID.class),
            rs.getLong("study_id"),
            rs.getInt("observation_id"),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            DbUtils.toInstant(rs.getTimestamp("created"))
    );
}
