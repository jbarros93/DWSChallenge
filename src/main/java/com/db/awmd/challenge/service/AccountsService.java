package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.exception.SameAccountException;
import com.db.awmd.challenge.exception.TransferNegativeAmountException;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class AccountsService {

    @Getter
    private final AccountsRepository accountsRepository;

    @Getter
    private final NotificationService notificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
        this.accountsRepository = accountsRepository;
        this.notificationService = notificationService;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    public ResponseEntity<Object> transfer(Transfer newTransfer)
            throws AccountNotFoundException, SameAccountException, InsufficientBalanceException, TransferNegativeAmountException {

        Account fromAccount = getAccount(newTransfer.getAccountFromId());
        Account toAccount = getAccount(newTransfer.getAccountToId());

        isValidTransfer(fromAccount, toAccount, newTransfer);

        Account lock1 = fromAccount;
        Account lock2 = toAccount;

        if(fromAccount.getAccountId().compareTo(toAccount.getAccountId()) < 0){
            lock1 = toAccount;
            lock2 = fromAccount;
        }

        synchronized (lock1){
          synchronized (lock2){
              log.info("New thread");

              if(!hasEnoughBalance(fromAccount, newTransfer)){
                  throw new InsufficientBalanceException("The source account does not have enough balance to make this transfer");
              }

              updateAccountsAndNotify(fromAccount, toAccount, newTransfer.getTransferAmount());
          }
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void updateAccountsAndNotify(Account fromAccount, Account toAccount, BigDecimal amount) {
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        List<Account> accountsToUpdate = new ArrayList<>();
        accountsToUpdate.add(fromAccount);
        accountsToUpdate.add(toAccount);
        accountsRepository.updateAccounts(accountsToUpdate);

        notificationService.notifyAboutTransfer(fromAccount, amount.toString());
        notificationService.notifyAboutTransfer(toAccount, amount.toString());
    }

    public void isValidTransfer(Account fromAccount, Account toAccount, Transfer newTransfer)
            throws AccountNotFoundException, SameAccountException, TransferNegativeAmountException {

        if (Objects.isNull(fromAccount)) {
            throw new AccountNotFoundException("The account from where the transfer should be made was not found");
        }

        if (Objects.isNull(toAccount)) {
            throw new AccountNotFoundException("The account to where the transfer should be made was not found");
        }

        if (fromAccount.getAccountId().equals(toAccount.getAccountId())) {
            throw new SameAccountException("The source and destination account should not be the same");
        }

        if (newTransfer.getTransferAmount().signum() <= 0) {
            throw new TransferNegativeAmountException("The transfer amount must be greater than 0");
        }
    }

    //check if the source account has enough money to make the transfer
    public boolean hasEnoughBalance(Account fromAccount, Transfer newTransfer){

        return (fromAccount.getBalance().subtract(newTransfer.getTransferAmount()).signum() >= 0);
    }
}

