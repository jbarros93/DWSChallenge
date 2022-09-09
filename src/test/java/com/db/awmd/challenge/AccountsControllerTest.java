package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.service.AccountsService;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class AccountsControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    public void createAccount() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

        Account account = accountsService.getAccount("Id-123");
        assertThat(account.getAccountId()).isEqualTo("Id-123");
        assertThat(account.getBalance()).isEqualByComparingTo("1000");
    }

    @Test
    public void createDuplicateAccount() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoAccountId() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoBalance() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoBody() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void transferNoBody() throws Exception {
        mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNegativeBalance() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountEmptyAccountId() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void getAccount() throws Exception {
        String uniqueAccountId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
        this.accountsService.createAccount(account);
        this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
                .andExpect(status().isOk())
                .andExpect(
                        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
    }

    @Test
    public void transfer() throws Exception {
        Account accountFrom = new Account("Id-300", new BigDecimal("1000"));
        Account accountTo = new Account("Id-301", new BigDecimal("2000"));
        accountsService.createAccount(accountFrom);
        accountsService.createAccount(accountTo);

        mockMvc.perform(post("/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"Id-300\",\"accountToId\":\"Id-301\",\"transferAmount\":60}"))
                        .andExpect(status().isOk());
    }

    @Test
    public void transferAccountNotFound() throws Exception {
        Account accountFrom = new Account("Id-302", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);

        mockMvc.perform(post("/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"Id-302\",\"accountToId\":\"Id-999\",\"transferAmount\":60}"))
                .andExpect(status().isNotFound());
    }


    @Test
    public void transferInsufficientBalance() throws Exception {
        Account accountFrom = new Account("Id-303", new BigDecimal("50"));
        accountsService.createAccount(accountFrom);
        Account accountTo = new Account("Id-304", new BigDecimal("1000"));
        accountsService.createAccount(accountTo);

        mockMvc.perform(post("/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"Id-303\",\"accountToId\":\"Id-304\",\"transferAmount\":60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void transferSameAccount() throws Exception {
        Account accountFrom = new Account("Id-305", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);

        mockMvc.perform(post("/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"Id-305\",\"accountToId\":\"Id-305\",\"transferAmount\":60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void transferNegativeAmount() throws Exception {
        Account accountFrom = new Account("Id-306", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);
        Account accountTo = new Account("Id-307", new BigDecimal("1000"));
        accountsService.createAccount(accountTo);

        mockMvc.perform(post("/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"Id-306\",\"accountToId\":\"Id-307\",\"transferAmount\":-60}"))
                .andExpect(status().isBadRequest());
    }
}
