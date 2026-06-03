package io.redlink.more.data.repository;

import io.redlink.more.data.model.goal.*;
import io.redlink.more.data.util.MapperUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GoalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedTemplate;

    // ==================== GOAL TEMPLATES ====================
    private static final String LIST_GOAL_TEMPLATES_FOR_GROUP = """
            SELECT gt.*,
                   ARRAY_AGG(gtog.observation_group_id) FILTER (WHERE gtog.observation_group_id IS NOT NULL) AS observation_group_ids,
                   ARRAY_AGG(gtt.key) FILTER (WHERE gtt.key IS NOT NULL) AS topic_keys,
                   ARRAY_AGG(gtac.check_id) FILTER (WHERE gtac.check_id IS NOT NULL) AS adherence_check_ids
            FROM goal_templates gt
                LEFT JOIN goal_template_observation_groups gtog ON gt.study_id = gtog.study_id AND gt.template_id = gtog.template_id
                LEFT JOIN goal_template_topics gtt ON gt.study_id = gtt.study_id AND gt.template_id = gtt.template_id
                LEFT JOIN goal_template_adherence_checks gtac ON gt.study_id = gtac.study_id AND gt.template_id = gtac.template_id
            WHERE gt.study_id = :study_id
              AND (gt.study_group_id IS NULL OR gt.study_group_id = :study_group_id)
              AND (NOT EXISTS (SELECT 1 FROM goal_template_observation_groups gtog3 
                               WHERE gtog3.study_id = gt.study_id AND gtog3.template_id = gt.template_id)
                   OR EXISTS (SELECT 1 FROM goal_template_observation_groups gtog2 
                              WHERE gtog2.study_id = gt.study_id 
                                AND gtog2.template_id = gt.template_id 
                                AND gtog2.observation_group_id = ANY(:observation_group_ids)))
            GROUP BY gt.study_id, gt.template_id""";

    private static final String GET_GOAL_TEMPLATE_BY_IDS = """
            SELECT gt.*,
                   ARRAY_AGG(gtog.observation_group_id) FILTER (WHERE gtog.observation_group_id IS NOT NULL) AS observation_group_ids,
                   ARRAY_AGG(gtt.key) FILTER (WHERE gtt.key IS NOT NULL) AS topic_keys,
                   ARRAY_AGG(gtac.check_id) FILTER (WHERE gtac.check_id IS NOT NULL) AS adherence_check_ids
            FROM goal_templates gt
                LEFT JOIN goal_template_observation_groups gtog ON gt.study_id = gtog.study_id AND gt.template_id = gtog.template_id
                LEFT JOIN goal_template_topics gtt ON gt.study_id = gtt.study_id AND gt.template_id = gtt.template_id
                LEFT JOIN goal_template_adherence_checks gtac ON gt.study_id = gtac.study_id AND gt.template_id = gtac.template_id
            WHERE gt.study_id = ? AND gt.template_id = ?
            GROUP BY gt.study_id, gt.template_id""";

    // ==================== GOALS (Read + Write) ====================

    private static final String INSERT_GOAL = """
            INSERT INTO goal(study_id, goal_id, participant_id, template_id, properties)
            VALUES (:study_id,
                    (SELECT COALESCE(MAX(goal_id),0)+1 FROM goal WHERE study_id = :study_id),
                    :participant_id, :template_id, :properties::jsonb)""";

    private static final String GET_GOAL_BY_ID = """
            SELECT g.*,
                   ARRAY_AGG(ggac.check_id) FILTER (WHERE ggac.check_id IS NOT NULL) AS adherence_check_ids
            FROM goal g
                LEFT JOIN goal_goal_adherence_checks ggac ON g.study_id = ggac.study_id AND g.goal_id = ggac.goal_id
            WHERE g.study_id = ? AND g.goal_id = ?
            GROUP BY g.study_id, g.goal_id""";

    private static final String LIST_GOALS = """
            SELECT g.*,
                   ARRAY_AGG(ggac.check_id) FILTER (WHERE ggac.check_id IS NOT NULL) AS adherence_check_ids
            FROM goal g
                LEFT JOIN goal_goal_adherence_checks ggac ON g.study_id = ggac.study_id AND g.goal_id = ggac.goal_id
            WHERE g.study_id = :study_id
              AND (:participant_id IS NULL OR g.participant_id = :participant_id)
              AND (:template_id IS NULL OR g.template_id = :template_id)
            GROUP BY g.study_id, g.goal_id""";

    private static final String UPDATE_GOAL = """
            UPDATE goal
            SET participant_id = :participant_id,
                template_id = :template_id,
                properties = :properties::jsonb, 
                modified = now() 
            WHERE study_id = :study_id AND goal_id = :goal_id""";

    private static final String DELETE_GOAL = "DELETE FROM goal WHERE study_id = ? AND goal_id = ?";

    private static final String DELETE_GOAL_ADHERENCE_CHECKS =
            "DELETE FROM goal_goal_adherence_checks WHERE study_id = :study_id AND goal_id = :goal_id";

    private static final String SET_GOAL_ADHERENCE_CHECKS =
            "INSERT INTO goal_goal_adherence_checks (study_id, goal_id, check_id) " +
                    "SELECT :study_id, :goal_id, unnest(:adherence_check_ids::int[])";

    // ==================== READ-ONLY CONFIG ====================

    private static final String LIST_GOAL_TOPICS = "SELECT * FROM goal_topics WHERE study_id = ? ORDER BY key";
    private static final String LIST_ADHERENCE_CHECKS = "SELECT * FROM goal_adherence_checks WHERE study_id = ? ORDER BY check_id";
    private static final String GET_STUDY_GOAL_CONFIG = "SELECT * FROM study_goal_config WHERE study_id = ?";

    private final RowMapper<GoalTemplate> goalTemplateRowMapper = (rs, rowNum) -> {
        try {
            return new GoalTemplate()
                    .setStudyId(rs.getLong("study_id"))
                    .setTemplateId(rs.getInt("template_id"))
                    .setTitle(rs.getString("title"))
                    .setParticipantTitle(rs.getString("participant_title"))
                    .setParticipantInfo(rs.getString("participant_info"))
                    .setType(rs.getString("type"))
                    .setKind(rs.getString("kind"))
                    .setStudyGroupId(rs.getObject("study_group_id", Integer.class))
                    .setProperties(DbUtils.readObject(rs, "properties") instanceof Map<?,?> map
                            ? map : new HashMap<String, Object>())
                    .setCreated(DbUtils.toInstant(rs.getTimestamp("created")))
                    .setModified(DbUtils.toInstant(rs.getTimestamp("modified")))
                    .setObservationGroupIds(DbUtils.readSet(rs, "observation_group_ids", Integer.class))
                    .setTopicKeys(DbUtils.readSet(rs, "topic_keys", String.class))
                    .setAdherenceCheckIds(DbUtils.readSet(rs, "adherence_check_ids", Integer.class));
        } catch (SQLException e) {
            throw new RuntimeException("Error mapping GoalTemplate", e);
        }
    };

    private final RowMapper<Goal> goalRowMapper = (rs, rowNum) -> {
        try {
            return new Goal()
                    .setStudyId(rs.getLong("study_id"))
                    .setGoalId(rs.getInt("goal_id"))
                    .setParticipantId(rs.getInt("participant_id"))
                    .setTemplateId(rs.getInt("template_id"))
                    .setTitle(rs.getString("title"))
                    .setProperties(DbUtils.readObject(rs, "properties") instanceof Map<?,?> map
                            ? map : new HashMap<String,Object>())
                    .setCreated(DbUtils.toInstant(rs.getTimestamp("created")))
                    .setModified(DbUtils.toInstant(rs.getTimestamp("modified")));
        } catch (SQLException e) {
            throw new RuntimeException("Error mapping Goal", e);
        }
    };

    private final RowMapper<GoalTopic> goalTopicRowMapper = (rs, rowNum) -> {
        try {
            return new GoalTopic()
                    .setStudyId(rs.getLong("study_id"))
                    .setKey(rs.getString("key"))
                    .setTitle(rs.getString("title"))
                    .setDescription(rs.getString("description"))
                    .setCreated(DbUtils.toInstant(rs.getTimestamp("created")))
                    .setModified(DbUtils.toInstant(rs.getTimestamp("modified")));
        } catch (SQLException e) {
            throw new RuntimeException("Error mapping GoalTopic", e);
        }
    };

    private final RowMapper<AdherenceCheck> checkRowMapper = (rs, rowNum) -> new AdherenceCheck()
            .setStudyId(rs.getLong("study_id"))
            .setCheckId(rs.getInt("check_id"))
            .setTitle(rs.getString("title"))
            .setTime(rs.getObject("time", java.time.LocalTime.class));

    private final RowMapper<StudyGoalConfig> configRowMapper = (rs, rowNum) -> new StudyGoalConfig()
            .setStudyId(rs.getLong("study_id"))
            .setCommitment(rs.getString("commitment"))
            .setAchievability(rs.getString("achievability"))
            .setUnderstandability(rs.getString("understandability"));

    public GoalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // ==================== GOAL TEMPLATES ====================

    public List<GoalTemplate> listGoalTemplatesForGroups(Long studyId, Integer studyGroupId, Collection<Integer> observationGroupIds) {
        Integer[] obsGroupArray = (observationGroupIds == null || observationGroupIds.isEmpty())
                ? new Integer[0]
                : observationGroupIds.toArray(new Integer[0]);

        return namedTemplate.query(
                LIST_GOAL_TEMPLATES_FOR_GROUP,
                new MapSqlParameterSource()
                        .addValue("study_id", studyId, Types.BIGINT)
                        .addValue("study_group_id", studyGroupId, Types.INTEGER)
                        .addValue("observation_group_ids", obsGroupArray, Types.ARRAY),
                goalTemplateRowMapper
        );
    }

    public GoalTemplate getGoalTemplateById(Long studyId, Integer templateId) {
        try {
            return jdbcTemplate.queryForObject(GET_GOAL_TEMPLATE_BY_IDS, goalTemplateRowMapper, studyId, templateId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }


    // ==================== GOALS (Full CRUD) ====================

    @Transactional
    public Goal insertGoal(Goal goal) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            namedTemplate.update(INSERT_GOAL,
                    new MapSqlParameterSource()
                            .addValue("study_id", goal.getStudyId(), Types.BIGINT)
                            .addValue("participant_id", goal.getParticipantId(), Types.INTEGER)
                            .addValue("template_id", goal.getTemplateId(), Types.INTEGER)
                            .addValue("properties", MapperUtils.writeValueAsString(goal.getProperties())),  // assuming you have write method or use ObjectMapper
                    keyHolder,
                    new String[]{"goal_id"});
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Failed to insert goal", e);
        }
        Integer goalId = Objects.requireNonNull(keyHolder.getKey()).intValue();
        setGoalAdherenceChecks(goal.getStudyId(), goalId, goal.getAdherenceCheckIds());
        return getGoalById(goal.getStudyId(), goalId);
    }

    public Goal getGoalById(Long studyId, Integer goalId) {
        try {
            return jdbcTemplate.queryForObject(GET_GOAL_BY_ID, goalRowMapper, studyId, goalId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Goal> listGoals(Long studyId, Integer participantId, Integer templateId) {
        return namedTemplate.query(
                LIST_GOALS,
                new MapSqlParameterSource()
                        .addValue("study_id", studyId, Types.BIGINT)
                        .addValue("participant_id", participantId, Types.INTEGER)
                        .addValue("template_id", templateId, Types.INTEGER),
                goalRowMapper
        );
    }

    @Transactional
    public Goal updateGoal(Goal goal) {
        namedTemplate.update(UPDATE_GOAL,
                new MapSqlParameterSource()
                        .addValue("study_id", goal.getStudyId(), Types.BIGINT)
                        .addValue("goal_id", goal.getGoalId(), Types.INTEGER)
                        .addValue("participant_id", goal.getParticipantId(), Types.INTEGER)
                        .addValue("template_id", goal.getTemplateId(), Types.INTEGER)
                        .addValue("properties", MapperUtils.writeValueAsString(goal.getProperties())));
        updateGoalAdherenceChecks(goal.getStudyId(), goal.getGoalId(), goal.getAdherenceCheckIds());
        return getGoalById(goal.getStudyId(), goal.getGoalId());
    }

    public void deleteGoal(Long studyId, Integer goalId) {
        jdbcTemplate.update(DELETE_GOAL, studyId, goalId);
    }

    /**
     * Sets the check ids for the parsed check ids for goal referenced by studyId and goalId
     * @param studyId
     * @param goalId
     * @param checkIds the checks Ids. Does nothing if NULL or empty
     */
    private void setGoalAdherenceChecks(Long studyId, Integer goalId, Collection<Integer> checkIds) {
        if (checkIds != null && !checkIds.isEmpty()) {
            final var params = new MapSqlParameterSource()
                    .addValue("study_id", studyId)
                    .addValue("goal_id", goalId);
            params.addValue("adherence_check_ids", checkIds.toArray(new Integer[0]));
            namedTemplate.update(SET_GOAL_ADHERENCE_CHECKS, params);
        } //else nothing to do
    }

    /**
     * Deletes existing and sets the parsed check ids for goal referenced by studyId and goalId
     * @param studyId
     * @param goalId
     * @param checkIds the checks Ids. NULL or empty if none
     */
    private void updateGoalAdherenceChecks(Long studyId, Integer goalId, Collection<Integer> checkIds) {
        final var params = new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("goal_id", goalId);
        namedTemplate.update(DELETE_GOAL_ADHERENCE_CHECKS, params);
        setGoalAdherenceChecks(studyId, goalId, checkIds);
    }

    // ==================== READ-ONLY CONFIG ====================

    public List<GoalTopic> listGoalTopics(Long studyId) {
        return jdbcTemplate.query(LIST_GOAL_TOPICS, goalTopicRowMapper, studyId);
    }

    public List<AdherenceCheck> listGoalAdherenceChecks(Long studyId) {
        return jdbcTemplate.query(LIST_ADHERENCE_CHECKS, checkRowMapper, studyId);
    }

    public StudyGoalConfig getStudyGoalConfig(Long studyId) {
        try {
            return jdbcTemplate.queryForObject(GET_STUDY_GOAL_CONFIG, configRowMapper, studyId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}