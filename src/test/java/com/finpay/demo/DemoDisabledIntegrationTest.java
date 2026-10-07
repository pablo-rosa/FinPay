package com.finpay.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!",
        "finpay.demo.enabled=false",
        "finpay.bootstrap.enabled=false"
})
@AutoConfigureMockMvc
class DemoDisabledIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void demoProvisioningRouteDoesNotExistWhenDemoModeIsDisabled() throws Exception {
        mockMvc.perform(post("/api/demo/session"))
                .andExpect(status().isNotFound());
    }
}
