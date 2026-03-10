package com.surver.sys.houduan.module.survey.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("!nodeps")
public class SurveyTitleRepairService {

    private final JdbcTemplate jdbcTemplate;

    public SurveyTitleRepairService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repairLegacySurveyTitles() {
        jdbcTemplate.query(
                "SELECT id, title FROM survey_info WHERE status <> 'DELETED'",
                rs -> {
                    long id = rs.getLong("id");
                    String title = rs.getString("title");
                    String repaired = SurveyTitleCodec.repairLegacyTitle(title, id);
                    if (!Objects.equals(repaired, title)) {
                        jdbcTemplate.update("UPDATE survey_info SET title = ? WHERE id = ?", repaired, id);
                    }
                }
        );
    }
}
