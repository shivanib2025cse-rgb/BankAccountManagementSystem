import java.time.LocalDate;

// ENCAPSULATION / DTO: Rich account representation joining customer and branch data
public class AccountDetail {
    private final int accountNo;
    private final int customerId;
    private final String customerName;
    private final String phone;
    private final String email;
    private final int branchId;
    private final String branchName;
    private final String ifscCode;
    private final String accountType;
    private final double balance;
    private final LocalDate openedOn;
    private final String status;

    public AccountDetail(int accountNo, int customerId, String customerName, String phone, String email,
                         int branchId, String branchName, String ifscCode, String accountType,
                         double balance, LocalDate openedOn, String status) {
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.customerName = customerName;
        this.phone = phone;
        this.email = email;
        this.branchId = branchId;
        this.branchName = branchName;
        this.ifscCode = ifscCode;
        this.accountType = accountType;
        this.balance = balance;
        this.openedOn = openedOn;
        this.status = (status != null && !status.isBlank()) ? status.toUpperCase() : "ACTIVE";
    }

    public AccountDetail(int accountNo, int customerId, String customerName, String phone, String email,
                         int branchId, String branchName, String ifscCode, String accountType,
                         double balance, LocalDate openedOn) {
        this(accountNo, customerId, customerName, phone, email, branchId, branchName, ifscCode, accountType, balance, openedOn, "ACTIVE");
    }

    public int getAccountNo() { return accountNo; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public int getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public String getIfscCode() { return ifscCode; }
    public String getAccountType() { return accountType; }
    public double getBalance() { return balance; }
    public LocalDate getOpenedOn() { return openedOn; }
    public String getStatus() { return status; }
}
