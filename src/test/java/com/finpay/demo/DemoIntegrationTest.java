package com.finpay.demo;

import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.domain.LedgerDirection;
import com.finpay.ledger.domain.LedgerTransactionType;
import com.finpay.ledger.infrastructure.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!",
        "finpay.demo.enabled=true",
        "finpay.demo.rate-limit.max-sessions=100"
})
@AutoConfigureMockMvc
@Transactional
class DemoIntegrationTest {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ACCOUNT_JSON = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\".*?\\\"balance\\\":([0-9.]+)", Pattern.DOTALL);
    private static final Pattern ENTITY_ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerTransactionRepository ledgerRepository;

    @Test
    void createsIsolatedUserAccountAndBalancedDemoFundingWithNormalUserJwt() throws Exception {
        DemoFixture first = createDemo();
        DemoFixture second = createDemo();

        assertThat(first.userId()).isNotEqualTo(second.userId());
        assertThat(first.fundedAccountId()).isNotEqualTo(second.fundedAccountId());
        assertThat(accountRepository.findById(first.fundedAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10000.0000");
        assertThat(accountRepository.findById(second.fundedAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10000.0000");
        assertThat(accountRepository.findById(first.destinationAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.0000");

        var funding = ledgerRepository.findByReferenceWithEntries("demo-funding:" + first.userId()).orElseThrow();
        assertThat(funding.getType()).isEqualTo(LedgerTransactionType.DEMO_FUNDING);
        assertThat(funding.getEntries()).hasSize(2);
        var accountEntry = funding.getEntries().stream().filter(entry -> entry.getAccount() != null).findFirst().orElseThrow();
        var counterEntry = funding.getEntries().stream().filter(entry -> entry.getAccount() == null).findFirst().orElseThrow();
        assertThat(accountEntry.getAccount().getId()).isEqualTo(first.fundedAccountId());
        assertThat(accountEntry.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(counterEntry.getCounterAccountCode()).isEqualTo("DEMO_CAPITAL");
        assertThat(counterEntry.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(accountEntry.getAmount()).isEqualByComparingTo(counterEntry.getAmount());
        assertThat(accountEntry.getAmount()).isEqualByComparingTo("10000.0000");

        mockMvc.perform(get("/api/admin/overview").header("Authorization", bearer(first.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousVisitorsReceiveIndependentFundedAccounts() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return createDemoInRolledBackTransaction(); });
            var second = executor.submit(() -> { start.await(); return createDemoInRolledBackTransaction(); });
            start.countDown();
            DemoFixture firstDemo = first.get();
            DemoFixture secondDemo = second.get();
            assertThat(firstDemo.userId()).isNotEqualTo(secondDemo.userId());
            assertThat(firstDemo.fundedBalance())
                    .isEqualByComparingTo("10000.0000");
            assertThat(secondDemo.fundedBalance())
                    .isEqualByComparingTo("10000.0000");
        }
    }

    @Test
    void demoCanTransferAndCompletePaymentThroughNormalApiAndCannotPatchBalance() throws Exception {
        DemoFixture demo = createDemo();
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(demo.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(demo.fundedAccountId(), demo.destinationAccountId(), "250.00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED"));

        String paymentBody = mockMvc.perform(post("/api/payments")
                                .header("Authorization", bearer(demo.token()))
                                .header("Idempotency-Key", "demo-payment-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferJson(demo.fundedAccountId(), demo.destinationAccountId(), "100.00")))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String paymentId = idFrom(paymentBody);
        paymentAction(demo.token(), paymentId, "submit").andExpect(status().isOk());
        paymentAction(demo.token(), paymentId, "authorize").andExpect(status().isOk());
        paymentAction(demo.token(), paymentId, "capture").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        paymentAction(demo.token(), paymentId, "complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(accountRepository.findById(demo.fundedAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("9650.0000");
        assertThat(accountRepository.findById(demo.destinationAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("350.0000");

        mockMvc.perform(patch("/api/accounts/{id}/balance", demo.fundedAccountId())
                        .header("Authorization", bearer(demo.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"balance\":999999.00}"))
                .andExpect(status().is4xxClientError());
        assertThat(accountRepository.findById(demo.fundedAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("9650.0000");
    }

    private DemoFixture createDemo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/demo/session"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.fundedAmount").value(10000))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        var tokenMatch = ACCESS_TOKEN.matcher(body);
        assertThat(tokenMatch.find()).isTrue();
        String token = tokenMatch.group(1);

        String profile = mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String accountBody = mockMvc.perform(get("/api/accounts").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID fundedId = null;
        UUID destinationId = null;
        var accountMatcher = ACCOUNT_JSON.matcher(accountBody);
        int accountCount = 0;
        while (accountMatcher.find()) {
            accountCount++;
            UUID id = UUID.fromString(accountMatcher.group(1));
            if (new BigDecimal(accountMatcher.group(2)).compareTo(BigDecimal.ZERO) > 0) fundedId = id;
            else destinationId = id;
        }
        assertThat(accountCount).isEqualTo(2);
        assertThat(fundedId).isNotNull();
        assertThat(destinationId).isNotNull();
        UUID userId = UUID.fromString(idFrom(profile));
        BigDecimal fundedBalance = accountRepository.findById(fundedId).orElseThrow().getBalance();
        return new DemoFixture(userId, token, fundedId, destinationId, fundedBalance);
    }

    private DemoFixture createDemoInRolledBackTransaction() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                DemoFixture fixture = createDemo();
                status.setRollbackOnly();
                return fixture;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static String idFrom(String json) {
        var matcher = ENTITY_ID.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions paymentAction(String token, String id, String action) throws Exception {
        return mockMvc.perform(post("/api/payments/{id}/{action}", id, action)
                .header("Authorization", bearer(token)));
    }

    private static String transferJson(UUID source, UUID destination, String amount) {
        return "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination
                + "\",\"amount\":" + amount + "}";
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record DemoFixture(UUID userId, String token, UUID fundedAccountId, UUID destinationAccountId,
                               BigDecimal fundedBalance) { }
}
