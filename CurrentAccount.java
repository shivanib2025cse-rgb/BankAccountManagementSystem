import java.time.LocalDate;

// INHERITANCE: CurrentAccount extends abstract class Account
public class CurrentAccount extends Account {
    private static final double OVERDRAFT_LIMIT = 10000.00;

    public CurrentAccount(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn, String status) {
        super(accountNo, customerId, branchId, balance, openedOn, status);
    }

    public CurrentAccount(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn) {
        super(accountNo, customerId, branchId, balance, openedOn);
    }

    // POLYMORPHISM: Concrete implementation of withdraw allowing overdraft up to Rs. 10,000
    @Override
    public void withdraw(double amount) throws InsufficientFundsException {
        if (!"ACTIVE".equalsIgnoreCase(getStatus())) {
            throw new IllegalStateException("Withdrawal rejected: Account #" + getAccountNo() + " is currently " + getStatus() + ".");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        if (getBalance() - amount < -OVERDRAFT_LIMIT) {
            throw new InsufficientFundsException(
                "Withdrawal rejected: Exceeds maximum overdraft limit of Rs. " + OVERDRAFT_LIMIT + 
                ". Current balance: Rs. " + getBalance()
            );
        }
        setBalance(getBalance() - amount);
    }

    // POLYMORPHISM: Current accounts earn no interest (0%)
    @Override
    public double calculateInterest() {
        return 0.0;
    }

    @Override
    public String getType() {
        return "CURRENT";
    }
}
