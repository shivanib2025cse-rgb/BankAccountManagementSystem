// ENCAPSULATION: Domain model representing a bank loan
public class Loan {
    private int loanId;
    private int customerId;
    private int branchId;
    private String loanType;
    private double amount;
    private double interestRate;
    private String status;

    public Loan(int loanId, int customerId, int branchId, String loanType, double amount, double interestRate, String status) {
        this.loanId = loanId;
        this.customerId = customerId;
        this.branchId = branchId;
        this.loanType = loanType;
        this.amount = amount;
        this.interestRate = interestRate;
        this.status = status;
    }

    public Loan(int customerId, int branchId, String loanType, double amount, double interestRate) {
        this(0, customerId, branchId, loanType, amount, interestRate, "PENDING");
    }

    public int getLoanId() { return loanId; }
    public int getCustomerId() { return customerId; }
    public int getBranchId() { return branchId; }
    public String getLoanType() { return loanType; }
    public double getAmount() { return amount; }
    public double getInterestRate() { return interestRate; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("Loan %d | Cust ID: %d | Branch ID: %d | Type: %s | Amount: Rs. %.2f | Rate: %.2f%% | Status: %s",
                loanId, customerId, branchId, loanType, amount, interestRate, status);
    }
}
