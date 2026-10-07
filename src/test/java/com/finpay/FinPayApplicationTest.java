package com.finpay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!")
@AutoConfigureMockMvc
class FinPayApplicationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthEndpointReportsApplicationAndDatabaseAsUp() throws Exception {
        var result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
        var appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finpay.flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class
        );
        assertThat(appliedMigrations).isEqualTo(1);
    }
}
