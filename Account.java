import java.time.LocalDate;

// ABSTRACTION: Abstract base class representing a bank account
public abstract class Account {
    // ENCAPSULATION: Private fields accessible only through getters
    private final int accountNo;
    private final int customerId;
    private final int branchId;
    private double balance;
    private final LocalDate openedOn;
    private String status = "ACTIVE"; // ACTIVE, FROZEN, CLOSED

    public Account(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn, String status) {
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.branchId = branchId;
        this.balance = balance;
        this.openedOn = openedOn;
        if (status != null && !status.isBlank()) {
            this.status = status.toUpperCase();
        }
    }

    public Account(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn) {
        this(accountNo, customerId, branchId, balance, openedOn, "ACTIVE");
    }

    // Getters for encapsulation
    public int getAccountNo() {
        return accountNo;
    }

    public int getCustomerId() {
        return customerId;
    }

    public int getBranchId() {
        return branchId;
    }

    public double getBalance() {
        return balance;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status != null) ? status.toUpperCase() : "ACTIVE";
    }

    protected void setBalance(double balance) {
        this.balance = balance;
    }

    // Common concrete method for deposit
    public void deposit(double amount) {
        if (!"ACTIVE".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Operation rejected: Account #" + accountNo + " is currently " + status + ".");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }
        this.balance += amount;
    }

    // ABSTRACTION: Abstract methods to be implemented by concrete subclasses
    // POLYMORPHISM: Dynamic dispatch enforced in SavingsAccount and CurrentAccount
    public abstract void withdraw(double amount) throws InsufficientFundsException;
    public abstract double calculateInterest();
    public abstract String getType();
}
