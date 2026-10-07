package com.finpay.payments;

import com.finpay.accounts.domain.Account;
import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.domain.LedgerTransactionType;
import com.finpay.ledger.infrastructure.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!")
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd-123!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ENTITY_ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void paymentFollowsStateMachineAndCapturePostsToLedgerOnce() throws Exception {
        Fixture fixture = createFixture();
        fund(fixture.accounts().getFirst(), "100.00");
        String key = "payment-" + UUID.randomUUID();

        MvcResult created = createPayment(fixture, key, "25.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        String paymentId = idFrom(created.getResponse().getContentAsString());

        createPayment(fixture, key, "25.0")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(paymentId));
        createPayment(fixture, key, "26.00")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        paymentAction(fixture, paymentId, "submit").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        paymentAction(fixture, paymentId, "authorize").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
        MvcResult captured = paymentAction(fixture, paymentId, "capture").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED")).andReturn();
        assertThat(captured.getResponse().getContentAsString()).contains("ledgerTransactionId");
        paymentAction(fixture, paymentId, "capture").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATE_TRANSITION"));
        paymentAction(fixture, paymentId, "complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(accountRepository.findById(fixture.accounts().getFirst().getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("75.0000");
        assertThat(accountRepository.findById(fixture.accounts().get(1).getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("25.0000");
        var ledger = ledgerTransactionRepository.findByReferenceWithEntries("payment:" + paymentId).orElseThrow();
        assertThat(ledger.getType()).isEqualTo(LedgerTransactionType.PAYMENT);
        assertThat(ledger.getEntries()).hasSize(2);

        createPayment(fixture, key, "25.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/payments/{id}", paymentId).header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void insufficientFundsRejectsPaymentAndInvalidTransitionsAreBlocked() throws Exception {
        Fixture fixture = createFixture();
        String createdBody = createPayment(fixture, "reject-" + UUID.randomUUID(), "1.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String paymentId = idFrom(createdBody);

        paymentAction(fixture, paymentId, "authorize").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATE_TRANSITION"));
        paymentAction(fixture, paymentId, "submit").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        paymentAction(fixture, paymentId, "authorize").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        paymentAction(fixture, paymentId, "complete").andExpect(status().isConflict());
        assertThat(ledgerTransactionRepository.findAll()).noneMatch(tx -> tx.getReference().equals("payment:" + paymentId));
    }

    @Test
    void captureFailsIfFundsAreSpentAfterAuthorization() throws Exception {
        Fixture fixture = createFixture();
        Account source = fixture.accounts().getFirst();
        fund(source, "50.00");
        String paymentId = idFrom(createPayment(fixture, "fail-" + UUID.randomUUID(), "20.00")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        paymentAction(fixture, paymentId, "submit").andExpect(status().isOk());
        paymentAction(fixture, paymentId, "authorize").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        source.applyLedgerEntry(new BigDecimal("-50.00"));
        accountRepository.saveAndFlush(source);
        paymentAction(fixture, paymentId, "capture").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(ledgerTransactionRepository.findByReferenceWithEntries("payment:" + paymentId)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOnePayment() throws Exception {
        Fixture fixture = createFixture();
        String key = "concurrent-" + UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return createPayment(fixture, key, "7.00").andReturn();
            });
            var second = executor.submit(() -> {
                start.await();
                return createPayment(fixture, key, "7.00").andReturn();
            });
            start.countDown();
            var firstResult = first.get();
            var secondResult = second.get();
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 200);
            assertThat(idFrom(firstResult.getResponse().getContentAsString()))
                    .isEqualTo(idFrom(secondResult.getResponse().getContentAsString()));
        }
        mockMvc.perform(get("/api/payments").header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(1));
    }

    private Fixture createFixture() throws Exception {
        String email = "finpay+" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tokenMatcher = ACCESS_TOKEN.matcher(loginBody);
        assertThat(tokenMatcher.find()).isTrue();
        String token = tokenMatcher.group(1);
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(post("/api/accounts").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"EUR\"}"))
                    .andExpect(status().isCreated()).andReturn();
            accounts.add(accountRepository.findById(UUID.fromString(idFrom(result.getResponse().getContentAsString()))).orElseThrow());
        }
        return new Fixture(token, accounts);
    }

    private org.springframework.test.web.servlet.ResultActions createPayment(Fixture fixture, String key, String amount) throws Exception {
        return mockMvc.perform(post("/api/payments").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + fixture.accounts().getFirst().getId()
                        + "\",\"destinationAccountId\":\"" + fixture.accounts().get(1).getId()
                        + "\",\"amount\":" + amount + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions paymentAction(Fixture fixture, String paymentId, String action) throws Exception {
        return mockMvc.perform(post("/api/payments/{id}/{action}", paymentId, action)
                .header("Authorization", bearer(fixture.token())));
    }

    private void fund(Account account, String amount) {
        account.applyLedgerEntry(new BigDecimal(amount));
        accountRepository.saveAndFlush(account);
    }

    private static String idFrom(String json) {
        var matcher = ENTITY_ID.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Fixture(String token, List<Account> accounts) { }
}
