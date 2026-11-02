import java.time.LocalDate;

// ENCAPSULATION / DOMAIN MODEL: Represents a Fixed Deposit (Term Investment) with compound interest
public class FixedDeposit {
    private int fdId;
    private final int accountNo;
    private final int customerId;
    private final double principalAmount;
    private final double interestRate; // e.g. 6.5% p.a.
    private final int tenureMonths;
    private final LocalDate startDate;
    private final LocalDate maturityDate;
    private final double maturityAmount;
    private String status; // ACTIVE, MATURED, PREMATURE_CLOSED

    public FixedDeposit(int fdId, int accountNo, int customerId, double principalAmount,
                        double interestRate, int tenureMonths, LocalDate startDate,
                        LocalDate maturityDate, double maturityAmount, String status) {
        this.fdId = fdId;
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.tenureMonths = tenureMonths;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.maturityAmount = maturityAmount;
        this.status = status;
    }

    public FixedDeposit(int accountNo, int customerId, double principalAmount,
                        double interestRate, int tenureMonths, LocalDate startDate) {
        this(0, accountNo, customerId, principalAmount, interestRate, tenureMonths,
             startDate, startDate.plusMonths(tenureMonths),
             calculateMaturityAmount(principalAmount, interestRate, tenureMonths), "ACTIVE");
    }

    // Compounded quarterly interest calculation: A = P * (1 + r/(100*4))^(4 * months/12)
    public static double calculateMaturityAmount(double principal, double annualRate, int months) {
        if (principal <= 0 || months <= 0) return principal;
        double quarterlyRate = (annualRate / 100.0) / 4.0;
        double quarters = (months / 12.0) * 4.0;
        double amount = principal * Math.pow(1.0 + quarterlyRate, quarters);
        return Math.round(amount * 100.0) / 100.0;
    }

    // Premature closure calculation with 1% penalty on interest
    public double calculatePrematureLiquidationAmount() {
        double penaltyRate = Math.max(1.0, interestRate - 1.0);
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, LocalDate.now());
        if (elapsedDays <= 7) {
            return principalAmount; // Minimum 7-day lock-in, returns principal
        }
        int elapsedMonths = Math.max(1, (int) (elapsedDays / 30));
        return calculateMaturityAmount(principalAmount, penaltyRate, elapsedMonths);
    }

    public int getFdId() { return fdId; }
    public void setFdId(int fdId) { this.fdId = fdId; }
    public int getAccountNo() { return accountNo; }
    public int getCustomerId() { return customerId; }
    public double getPrincipalAmount() { return principalAmount; }
    public double getInterestRate() { return interestRate; }
    public int getTenureMonths() { return tenureMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public double getMaturityAmount() { return maturityAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
