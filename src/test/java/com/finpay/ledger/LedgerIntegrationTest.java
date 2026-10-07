package com.finpay.ledger;

import com.finpay.AbstractPostgresIntegrationTest;
import com.finpay.accounts.domain.Account;
import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.application.LedgerPosting;
import com.finpay.ledger.application.LedgerPostingException;
import com.finpay.ledger.application.LedgerService;
import com.finpay.ledger.domain.LedgerDirection;
import com.finpay.ledger.infrastructure.LedgerTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finpay.security.jwt.secret=finpay-test-secret-key-32-bytes!!")
@AutoConfigureMockMvc
@Transactional
class LedgerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd-123!";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private LedgerService ledgerService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerTransactionRepository transactionRepository;

    @Test
    void postsBalancedEntriesAndUpdatesAccountBalancesAtomically() throws Exception {
        List<Account> accounts = createAccounts("EUR", 2);
        Account debit = accounts.get(0);
        Account credit = accounts.get(1);
        debit.applyLedgerEntry(new BigDecimal("100.0000"));
        accountRepository.saveAndFlush(debit);

        ledgerService.post("opening-" + UUID.randomUUID(), List.of(
                posting(debit, "25.00", LedgerDirection.DEBIT),
                posting(credit, "25.00", LedgerDirection.CREDIT)
        ));

        assertThat(accountRepository.findById(debit.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("75.0000");
        assertThat(accountRepository.findById(credit.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("25.0000");
        var transaction = transactionRepository.findAll().stream()
                .filter(candidate -> candidate.getReference().startsWith("opening-"))
                .findFirst().orElseThrow();
        assertThat(transaction.getEntries()).hasSize(2);
        assertThat(transaction.getStatus().name()).isEqualTo("POSTED");
    }

    @Test
    void rejectsUnbalancedAndInsufficientDebitWithoutPersistingEntries() throws Exception {
        List<Account> accounts = createAccounts("EUR", 2);
        long initialTransactions = transactionRepository.count();
        assertThatThrownBy(() -> ledgerService.post("unbalanced-" + UUID.randomUUID(), List.of(
                posting(accounts.get(0), "10.00", LedgerDirection.DEBIT),
                posting(accounts.get(1), "9.00", LedgerDirection.CREDIT))))
                .isInstanceOf(LedgerPostingException.class)
                .hasMessageContaining("Total debits must equal total credits");

        assertThatThrownBy(() -> ledgerService.post("insufficient-" + UUID.randomUUID(), List.of(
                posting(accounts.get(0), "10.00", LedgerDirection.DEBIT),
                posting(accounts.get(1), "10.00", LedgerDirection.CREDIT))))
                .isInstanceOf(LedgerPostingException.class)
                .hasMessageContaining("negative");
        assertThat(transactionRepository.count()).isEqualTo(initialTransactions);
    }

    @Test
    void rejectsDuplicateReferencesAndCurrencyMismatch() throws Exception {
        List<Account> eurAccounts = createAccounts("EUR", 2);
        List<Account> usdAccounts = createAccounts("USD", 1);
        eurAccounts.get(0).applyLedgerEntry(new BigDecimal("10.0000"));
        accountRepository.saveAndFlush(eurAccounts.get(0));
        String reference = "unique-" + UUID.randomUUID();
        var postings = List.of(
                posting(eurAccounts.get(0), "5.00", LedgerDirection.DEBIT),
                posting(eurAccounts.get(1), "5.00", LedgerDirection.CREDIT));
        ledgerService.post(reference, postings);
        assertThatThrownBy(() -> ledgerService.post(reference, postings))
                .isInstanceOf(LedgerPostingException.class)
                .hasMessageContaining("already exists");

        assertThatThrownBy(() -> ledgerService.post("currency-" + UUID.randomUUID(), List.of(
                posting(eurAccounts.get(0), "5.00", LedgerDirection.DEBIT),
                posting(usdAccounts.get(0), "5.00", LedgerDirection.CREDIT))))
                .isInstanceOf(LedgerPostingException.class)
                .hasMessageContaining("one currency");
    }

    private List<Account> createAccounts(String currency, int count) throws Exception {
        String email = "finpay+" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = ACCESS_TOKEN.matcher(loginBody);
        assertThat(matcher.find()).isTrue();
        String token = matcher.group(1);

        for (int i = 0; i < count; i++) {
            mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\":\"" + currency + "\"}"))
                    .andExpect(status().isCreated());
        }
        UUID userId = UUID.fromString(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));
        return accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    private static LedgerPosting posting(Account account, String amount, LedgerDirection direction) {
        return new LedgerPosting(account.getId(), new BigDecimal(amount), direction);
    }
}
