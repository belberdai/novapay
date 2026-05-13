package dev.novapay.payments.account;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_number", length = 32, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "currency", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() { }

    public static Account create(String accountNumber, String currency) {
        Account account = new Account();
        account.accountNumber = accountNumber;
        account.currency = currency;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = Instant.now();
        return account;
    }

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}