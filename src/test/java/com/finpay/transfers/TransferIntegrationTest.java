package com.finpay.transfers;

import com.finpay.accounts.domain.Account;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest(properties = "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!")
@AutoConfigureMockMvc
@Transactional
class TransferIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd-123!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ENTITY_ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void transferPostsBalancedLedgerEntriesAndReturnsOwnHistory() throws Exception {
        Fixture fixture = createFixture("EUR", "EUR");
        fund(fixture.accounts().getFirst(), "100.00");

        MvcResult result = transfer(fixture, 0, 1, "25.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(25))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        UUID transferId = UUID.fromString(idFrom(result.getResponse().getContentAsString()));

        assertThat(balance(fixture.accounts().getFirst().getId())).isEqualByComparingTo("75.0000");
        assertThat(balance(fixture.accounts().get(1).getId())).isEqualByComparingTo("25.0000");
        mockMvc.perform(get("/api/transfers/{id}", transferId).header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transferId.toString()));
        mockMvc.perform(get("/api/transfers").header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].id").value(transferId.toString()));

        var ledger = ledgerTransactionRepository.findByReferenceWithEntries("transfer:" + transferId).orElseThrow();
        assertThat(ledger.getType()).isEqualTo(LedgerTransactionType.TRANSFER);
        assertThat(ledger.getEntries()).hasSize(2);
        assertThat(ledger.getEntries()).extracting(entry -> entry.getDirection())
                .containsExactlyInAnyOrder(LedgerDirection.DEBIT, LedgerDirection.CREDIT);
        assertThat(ledger.getEntries()).extracting(entry -> entry.getAmount())
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("25.0000"));
    }

    @Test
    void rejectsInsufficientFundsSelfTransferAndCurrencyMismatch() throws Exception {
        Fixture fixture = createFixture("EUR", "EUR", "USD");
        UUID source = fixture.accounts().getFirst().getId();

        transfer(fixture, 0, 1, "1.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(fixture.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, source, "1.00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_TRANSFER"));
        transfer(fixture, 0, 2, "1.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_CURRENCY_MISMATCH"));
        transfer(fixture, 0, 1, "0.00001").andExpect(status().isBadRequest());
    }

    @Test
    void sourceMustBelongToCallerAndBothAccountsMustBeActive() throws Exception {
        Fixture owner = createFixture("EUR", "EUR");
        Fixture other = createFixture("EUR", "EUR");
        fund(owner.accounts().getFirst(), "20.00");

        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(owner.accounts().getFirst().getId(), owner.accounts().get(1).getId(), "5.00")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSFER_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(patch("/api/accounts/{id}/status", owner.accounts().get(1).getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());
        transfer(owner, 0, 1, "5.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransfersCannotSpendTheSameBalanceTwice() throws Exception {
        Fixture fixture = createFixture("EUR", "EUR", "EUR");
        fund(fixture.accounts().getFirst(), "100.00");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return transfer(fixture, 0, 1, "80.00").andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return transfer(fixture, 0, 2, "80.00").andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(balance(fixture.accounts().getFirst().getId())).isEqualByComparingTo("20.0000");
        assertThat(balance(fixture.accounts().get(1).getId()).add(balance(fixture.accounts().get(2).getId())))
                .isEqualByComparingTo("80.0000");
    }

    private Fixture createFixture(String... currencies) throws Exception {
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
        for (String currency : currencies) {
            MvcResult response = mockMvc.perform(post("/api/accounts").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"" + currency + "\"}"))
                    .andExpect(status().isCreated()).andReturn();
            accounts.add(accountRepository.findById(UUID.fromString(idFrom(response.getResponse().getContentAsString()))).orElseThrow());
        }
        return new Fixture(token, accounts);
    }

    private void fund(Account account, String amount) {
        account.applyLedgerEntry(new BigDecimal(amount));
        accountRepository.saveAndFlush(account);
    }

    private org.springframework.test.web.servlet.ResultActions transfer(Fixture fixture, int source, int destination, String amount) throws Exception {
        return mockMvc.perform(post("/api/transfers").header("Authorization", bearer(fixture.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(fixture.accounts().get(source).getId(), fixture.accounts().get(destination).getId(), amount)));
    }

    private BigDecimal balance(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private static String transferJson(UUID source, UUID destination, String amount) {
        return "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination
                + "\",\"amount\":" + amount + "}";
    }

    private static String idFrom(String json) {
        var matcher = ENTITY_ID.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Fixture(String token, List<Account> accounts) { }
}
