import java.time.LocalDate;

// INHERITANCE: SavingsAccount extends abstract class Account
public class SavingsAccount extends Account {
    private static final double MINIMUM_BALANCE = 1000.00;
    private static final double INTEREST_RATE = 0.04; // 4% per annum

    public SavingsAccount(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn, String status) {
        super(accountNo, customerId, branchId, balance, openedOn, status);
    }

    public SavingsAccount(int accountNo, int customerId, int branchId, double balance, LocalDate openedOn) {
        super(accountNo, customerId, branchId, balance, openedOn);
    }

    // POLYMORPHISM: Concrete implementation of withdraw with minimum balance rule
    @Override
    public void withdraw(double amount) throws InsufficientFundsException {
        if (!"ACTIVE".equalsIgnoreCase(getStatus())) {
            throw new IllegalStateException("Withdrawal rejected: Account #" + getAccountNo() + " is currently " + getStatus() + ".");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        if (getBalance() - amount < MINIMUM_BALANCE) {
            throw new InsufficientFundsException(
                "Withdrawal rejected: Savings Account requires a minimum balance of Rs. " + MINIMUM_BALANCE + 
                ". Current balance: Rs. " + getBalance()
            );
        }
        setBalance(getBalance() - amount);
    }

    // POLYMORPHISM: Calculates 4% yearly interest
    @Override
    public double calculateInterest() {
        return getBalance() * INTEREST_RATE;
    }

    @Override
    public String getType() {
        return "SAVINGS";
    }
}
