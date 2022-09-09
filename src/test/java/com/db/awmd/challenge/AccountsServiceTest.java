package com.db.awmd.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.*;
import com.db.awmd.challenge.service.AccountsService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;

    static final Logger logger = LoggerFactory.getLogger(AccountsServiceTest.class);

    @Test
    public void addAccount() throws Exception {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    public void addAccount_failsOnDuplicateId() throws Exception {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }

    }

    @Test
    public void transferTest()
            throws TransferNegativeAmountException, SameAccountException, InsufficientBalanceException, AccountNotFoundException {
        Account accountFrom = new Account("Id-200", new BigDecimal("1000"));
        Account accountTo = new Account("Id-201", new BigDecimal("2000"));
        accountsService.createAccount(accountFrom);
        accountsService.createAccount(accountTo);
        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("10"));

        accountsService.transfer(newTransfer);

        assertEquals(BigDecimal.valueOf(990), accountFrom.getBalance());
        assertEquals(BigDecimal.valueOf(2010), accountTo.getBalance());
    }

    @Test(expected = AccountNotFoundException.class)
    public void transferTestAccountNotFound()
            throws AccountNotFoundException, TransferNegativeAmountException, SameAccountException, InsufficientBalanceException {
        Account accountFrom = new Account("Id-202", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);

        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), "No-Account", new BigDecimal("60"));
        accountsService.transfer(newTransfer);
    }

    @Test(expected = DuplicateAccountIdException.class)
    public void transferTestDuplicateAccountId() {
        Account accountFrom = new Account("Id-203", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);
        accountsService.createAccount(accountFrom);
    }

    @Test(expected = InsufficientBalanceException.class)
    public void transferTestInsufficientBalance()
            throws AccountNotFoundException, TransferNegativeAmountException, SameAccountException, InsufficientBalanceException {
        Account accountFrom = new Account("Id-204", new BigDecimal("50"));
        Account accountTo = new Account("Id-205", new BigDecimal("50"));
        accountsService.createAccount(accountFrom);
        accountsService.createAccount(accountTo);

        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("60"));
        accountsService.transfer(newTransfer);
    }

    @Test(expected = SameAccountException.class)
    public void transferTestSameAccount()
            throws AccountNotFoundException, TransferNegativeAmountException, SameAccountException, InsufficientBalanceException {
        Account accountFrom = new Account("Id-206", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);

        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), accountFrom.getAccountId(), new BigDecimal("60"));
        accountsService.transfer(newTransfer);
    }

    @Test(expected = TransferNegativeAmountException.class)
    public void transferTestTransferNegativeAmount()
            throws AccountNotFoundException, TransferNegativeAmountException, SameAccountException, InsufficientBalanceException {
        Account accountFrom = new Account("Id-207", new BigDecimal("1000"));
        accountsService.createAccount(accountFrom);
        Account accountTo = new Account("Id-208", new BigDecimal("1000"));
        accountsService.createAccount(accountTo);

        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("-60"));
        accountsService.transfer(newTransfer);
    }

    @Test
    public void transferTestConcurrentThreads()
            throws InterruptedException {
        Account accountFrom = new Account("Id-209", new BigDecimal("1000"));
        Account accountTo = new Account("Id-210", new BigDecimal("2000"));
        accountsService.createAccount(accountFrom);
        accountsService.createAccount(accountTo);

        Transfer newTransfer = new Transfer(accountFrom.getAccountId(), accountTo.getAccountId(), new BigDecimal("100"));

        concurrentThreads(newTransfer, 50);

        assertEquals(new BigDecimal(0), accountFrom.getBalance());
        assertEquals(new BigDecimal(3000), accountTo.getBalance());
    }

    private void concurrentThreads(Transfer newTransfer, int n)
            throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            service.execute(() -> {
                try {
                    accountsService.transfer(newTransfer);
                } catch (TransferNegativeAmountException | SameAccountException | InsufficientBalanceException | AccountNotFoundException e) {
                    logger.info(e.getMessage());
                }
                latch.countDown();
            });
        }
        latch.await();
    }
}
