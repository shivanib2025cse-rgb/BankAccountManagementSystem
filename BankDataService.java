import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// SERVICE LAYER: Unified interface for Bank data with automatic MySQL detection & resilient fallback
public class BankDataService {

    private final BankDAO bankDAO;
    private boolean useMySQL;

    // In-memory fallback repositories (thread-safe)
    private final Map<Integer, Customer> memCustomers = new ConcurrentHashMap<>();
    private final Map<Integer, Branch> memBranches = new ConcurrentHashMap<>();
    private final Map<Integer, Account> memAccounts = new ConcurrentHashMap<>();
    private final Map<Integer, FixedDeposit> memFixedDeposits = new ConcurrentHashMap<>();
    private final List<Transaction> memTransactions = Collections.synchronizedList(new ArrayList<>());
    private final List<Loan> memLoans = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger customerIdSeq = new AtomicInteger(3);
    private final AtomicInteger branchIdSeq = new AtomicInteger(2);
    private final AtomicInteger accountNoSeq = new AtomicInteger(1003);
    private final AtomicInteger loanIdSeq = new AtomicInteger(1);
    private final AtomicInteger txnIdSeq = new AtomicInteger(3);
    private final AtomicInteger fdIdSeq = new AtomicInteger(1);

    public BankDataService() {
        this.bankDAO = new BankDAO();
        checkConnection();
        if (!useMySQL) {
            initFallbackData();
        }
    }

    public synchronized boolean checkConnection() {
        try {
            boolean connected = DBConnection.testConnection();
            if (connected) {
                bankDAO.getAllBranches();
                this.useMySQL = true;
                System.out.println("[BankDataService] Connected to MySQL database successfully.");
            } else {
                this.useMySQL = false;
                System.out.println("[BankDataService] MySQL unreachable. Operating in InMemory Simulation Mode.");
            }
        } catch (Throwable t) {
            this.useMySQL = false;
            System.out.println("[BankDataService] MySQL connection check failed (" + t.getMessage() + "). Operating in InMemory Simulation Mode.");
        }
        return this.useMySQL;
    }

    public boolean isUsingMySQL() {
        return useMySQL;
    }

    public BankDAO getBankDAO() {
        return bankDAO;
    }

    private void initFallbackData() {
        // Seed Branches (matches schema.sql)
        Branch b1 = new Branch(1, "Main Branch", "BANK0001001", "Mumbai");
        Branch b2 = new Branch(2, "City Branch", "BANK0001002", "Delhi");
        memBranches.put(1, b1);
        memBranches.put(2, b2);

        // Seed Customers (matches schema.sql)
        Customer c1 = new Customer(1, "Alice Smith", "9876543210", "alice@example.com", "123 Park Street, Mumbai");
        Customer c2 = new Customer(2, "Bob Jones", "9876543211", "bob@example.com", "456 Hill Road, Delhi");
        Customer c3 = new Customer(3, "Charlie Brown", "9876543212", "charlie@example.com", "789 Lake View, Mumbai");
        memCustomers.put(1, c1);
        memCustomers.put(2, c2);
        memCustomers.put(3, c3);

        // Seed Accounts (matches schema.sql)
        LocalDate today = LocalDate.now();
        Account a1 = new SavingsAccount(1001, 1, 1, 5000.00, today, "ACTIVE");
        Account a2 = new CurrentAccount(1002, 2, 2, 15000.00, today, "ACTIVE");
        Account a3 = new SavingsAccount(1003, 3, 1, 3000.00, today, "ACTIVE");
        memAccounts.put(1001, a1);
        memAccounts.put(1002, a2);
        memAccounts.put(1003, a3);

        // Seed Transactions (matches schema.sql)
        memTransactions.add(new Transaction(1, 1001, "DEPOSIT", 5000.00, new Timestamp(System.currentTimeMillis() - 86400000L * 2), 5000.00));
        memTransactions.add(new Transaction(2, 1002, "DEPOSIT", 15000.00, new Timestamp(System.currentTimeMillis() - 86400000L), 15000.00));
        memTransactions.add(new Transaction(3, 1003, "DEPOSIT", 3000.00, new Timestamp(System.currentTimeMillis() - 3600000L * 4), 3000.00));

        // Sample Loan
        memLoans.add(new Loan(1, 1, 1, "HOME", 250000.00, 8.50, "APPROVED"));

        // Sample Fixed Deposit (1 Year @ 6.5%)
        FixedDeposit fd1 = new FixedDeposit(1, 1001, 1, 10000.00, 6.50, 12, today.minusMonths(2), today.plusMonths(10), 10666.00, "ACTIVE");
        memFixedDeposits.put(1, fd1);
    }

    // --- CUSTOMERS ---

    public List<Customer> getAllCustomers() throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAllCustomers(); } catch (SQLException e) { useMySQL = false; }
        }
        return new ArrayList<>(memCustomers.values());
    }

    public Customer getCustomerById(int customerId) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getCustomerById(customerId); } catch (SQLException e) { useMySQL = false; }
        }
        return memCustomers.get(customerId);
    }

    public int addCustomer(Customer customer) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.addCustomer(customer); } catch (SQLException e) { useMySQL = false; }
        }
        for (Customer c : memCustomers.values()) {
            if (c.getPhone().equals(customer.getPhone())) {
                throw new SQLException("Duplicate entry '" + customer.getPhone() + "' for key 'customer.phone'");
            }
        }
        int newId = customerIdSeq.incrementAndGet();
        Customer created = new Customer(newId, customer.getName(), customer.getPhone(), customer.getEmail(), customer.getAddress());
        memCustomers.put(newId, created);
        return newId;
    }

    public boolean updateCustomer(int customerId, String name, String phone, String email, String address) throws SQLException {
        Customer existing = memCustomers.get(customerId);
        if (existing == null) return false;
        Customer updated = new Customer(customerId, name, phone, email, address);
        memCustomers.put(customerId, updated);
        return true;
    }

    public Map<String, Object> getCustomerPortfolio(int customerId) throws SQLException {
        Customer c = getCustomerById(customerId);
        if (c == null) return null;

        Map<String, Object> portfolio = new LinkedHashMap<>();
        portfolio.put("customer", c);

        List<AccountDetail> accs = new ArrayList<>();
        for (AccountDetail ad : getAllAccountsDetailed()) {
            if (ad.getCustomerId() == customerId) accs.add(ad);
        }
        portfolio.put("accounts", accs);

        List<FixedDeposit> fds = new ArrayList<>();
        for (FixedDeposit fd : memFixedDeposits.values()) {
            if (fd.getCustomerId() == customerId) fds.add(fd);
        }
        portfolio.put("fixedDeposits", fds);

        List<Loan> loans = getCustomerLoans(customerId);
        portfolio.put("loans", loans);

        double totalDeposits = accs.stream().mapToDouble(AccountDetail::getBalance).sum();
        double totalFds = fds.stream().filter(f -> "ACTIVE".equals(f.getStatus())).mapToDouble(FixedDeposit::getPrincipalAmount).sum();
        double totalLoanDebt = loans.stream().filter(l -> "APPROVED".equals(l.getStatus())).mapToDouble(Loan::getAmount).sum();

        portfolio.put("totalDeposits", totalDeposits);
        portfolio.put("totalInvestments", totalFds);
        portfolio.put("totalDebt", totalLoanDebt);
        portfolio.put("netWorth", (totalDeposits + totalFds) - totalLoanDebt);

        return portfolio;
    }

    // --- BRANCHES ---

    public List<Branch> getAllBranches() throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAllBranches(); } catch (SQLException e) { useMySQL = false; }
        }
        return new ArrayList<>(memBranches.values());
    }

    public int addBranch(Branch branch) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.addBranch(branch); } catch (SQLException e) { useMySQL = false; }
        }
        for (Branch b : memBranches.values()) {
            if (b.getIfscCode().equalsIgnoreCase(branch.getIfscCode())) {
                throw new SQLException("Duplicate entry '" + branch.getIfscCode() + "' for key 'branch.ifsc_code'");
            }
        }
        int newId = branchIdSeq.incrementAndGet();
        Branch created = new Branch(newId, branch.getBranchName(), branch.getIfscCode(), branch.getCity());
        memBranches.put(newId, created);
        return newId;
    }

    // --- ACCOUNTS ---

    public List<AccountDetail> getAllAccountsDetailed() throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAllAccountsDetailed(); } catch (SQLException e) { useMySQL = false; }
        }
        List<AccountDetail> list = new ArrayList<>();
        for (Account a : memAccounts.values()) {
            Customer c = memCustomers.get(a.getCustomerId());
            Branch b = memBranches.get(a.getBranchId());
            list.add(new AccountDetail(
                a.getAccountNo(),
                a.getCustomerId(),
                c != null ? c.getName() : "Unknown",
                c != null ? c.getPhone() : "",
                c != null ? c.getEmail() : "",
                a.getBranchId(),
                b != null ? b.getBranchName() : "Unknown",
                b != null ? b.getIfscCode() : "",
                a.getType(),
                a.getBalance(),
                a.getOpenedOn(),
                a.getStatus()
            ));
        }
        list.sort(Comparator.comparingInt(AccountDetail::getAccountNo));
        return list;
    }

    public AccountDetail getAccountDetail(int accountNo) throws SQLException {
        if (useMySQL) {
            return bankDAO.getAccountDetail(accountNo);
        }
        Account a = memAccounts.get(accountNo);
        if (a == null) return null;
        Customer c = memCustomers.get(a.getCustomerId());
        Branch b = memBranches.get(a.getBranchId());
        return new AccountDetail(
            a.getAccountNo(),
            a.getCustomerId(),
            c != null ? c.getName() : "Unknown",
            c != null ? c.getPhone() : "",
            c != null ? c.getEmail() : "",
            a.getBranchId(),
            b != null ? b.getBranchName() : "Unknown",
            b != null ? b.getIfscCode() : "",
            a.getType(),
            a.getBalance(),
            a.getOpenedOn(),
            a.getStatus()
        );
    }

    public Account getAccount(int accountNo) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAccount(accountNo); } catch (SQLException e) { useMySQL = false; }
        }
        return memAccounts.get(accountNo);
    }

    public int openAccount(Account account) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.openAccount(account); } catch (SQLException e) { useMySQL = false; }
        }
        if (!memCustomers.containsKey(account.getCustomerId())) {
            throw new SQLException("Customer ID " + account.getCustomerId() + " does not exist.");
        }
        if (!memBranches.containsKey(account.getBranchId())) {
            throw new SQLException("Branch ID " + account.getBranchId() + " does not exist.");
        }

        int newAccNo = accountNoSeq.incrementAndGet();
        Account created;
        if ("SAVINGS".equalsIgnoreCase(account.getType())) {
            created = new SavingsAccount(newAccNo, account.getCustomerId(), account.getBranchId(), account.getBalance(), account.getOpenedOn(), "ACTIVE");
        } else {
            created = new CurrentAccount(newAccNo, account.getCustomerId(), account.getBranchId(), account.getBalance(), account.getOpenedOn(), "ACTIVE");
        }
        memAccounts.put(newAccNo, created);

        if (created.getBalance() > 0) {
            memTransactions.add(new Transaction(
                txnIdSeq.incrementAndGet(),
                newAccNo,
                "DEPOSIT",
                created.getBalance(),
                new Timestamp(System.currentTimeMillis()),
                created.getBalance()
            ));
        }
        return newAccNo;
    }

    public boolean updateAccountStatus(int accountNo, String newStatus) throws SQLException {
        if (useMySQL) {
            return bankDAO.updateAccountStatus(accountNo, newStatus);
        }
        Account acc = memAccounts.get(accountNo);
        if (acc == null) return false;
        acc.setStatus(newStatus);
        return true;
    }

    public boolean deposit(int accountNo, double amount) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.deposit(accountNo, amount); } catch (SQLException e) { useMySQL = false; }
        }
        Account acc = memAccounts.get(accountNo);
        if (acc == null) return false;
        acc.deposit(amount);
        // trg_log_transaction simulation
        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            accountNo,
            "DEPOSIT",
            amount,
            new Timestamp(System.currentTimeMillis()),
            acc.getBalance()
        ));
        return true;
    }

    public boolean withdraw(int accountNo, double amount) throws SQLException, InsufficientFundsException {
        if (useMySQL) {
            try { return bankDAO.withdraw(accountNo, amount); } catch (SQLException e) { useMySQL = false; }
        }
        Account acc = memAccounts.get(accountNo);
        if (acc == null) {
            throw new IllegalArgumentException("Account #" + accountNo + " does not exist.");
        }
        acc.withdraw(amount);
        // trg_log_transaction simulation
        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            accountNo,
            "WITHDRAW",
            amount,
            new Timestamp(System.currentTimeMillis()),
            acc.getBalance()
        ));
        return true;
    }

    // sp_transfer_funds simulation
    public synchronized String transferFunds(int fromAcc, int toAcc, double amount) throws SQLException {
        if (useMySQL) {
            return bankDAO.transferFunds(fromAcc, toAcc, amount);
        }
        if (amount <= 0) {
            return "ERROR: Transfer amount must be positive.";
        }
        if (fromAcc == toAcc) {
            return "ERROR: Source and target account cannot be identical.";
        }
        Account source = memAccounts.get(fromAcc);
        if (source == null) {
            return "ERROR: Source account does not exist.";
        }
        Account target = memAccounts.get(toAcc);
        if (target == null) {
            return "ERROR: Target account does not exist.";
        }
        try {
            source.withdraw(amount);
        } catch (InsufficientFundsException | IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }

        try {
            target.deposit(amount);
        } catch (IllegalStateException e) {
            // Rollback source if target is frozen
            source.deposit(amount);
            return "ERROR: Target account cannot receive funds (" + e.getMessage() + ")";
        }

        // trg_log_transaction for both accounts
        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            fromAcc,
            "TRANSFER_OUT",
            amount,
            new Timestamp(System.currentTimeMillis()),
            source.getBalance()
        ));
        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            toAcc,
            "TRANSFER_IN",
            amount,
            new Timestamp(System.currentTimeMillis()),
            target.getBalance()
        ));

        return "SUCCESS: Fund transfer completed successfully.";
    }

    // sp_mini_statement simulation
    public List<Transaction> getMiniStatement(int accountNo) throws SQLException {
        if (useMySQL) {
            return bankDAO.getMiniStatement(accountNo);
        }
        List<Transaction> list = new ArrayList<>();
        synchronized (memTransactions) {
            for (Transaction t : memTransactions) {
                if (t.getAccountNo() == accountNo) {
                    list.add(t);
                }
            }
        }
        list.sort((a, b) -> b.getTxnDate().compareTo(a.getTxnDate()));
        if (list.size() > 10) {
            return new ArrayList<>(list.subList(0, 10));
        }
        return list;
    }

    // Polymorphism apply interest
    public double applyYearlyInterest(int accountNo) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.applyYearlyInterest(accountNo); } catch (SQLException e) { useMySQL = false; }
        }
        Account acc = memAccounts.get(accountNo);
        if (acc == null) return -1.0;
        double interest = acc.calculateInterest();
        if (interest > 0) {
            acc.deposit(interest);
            memTransactions.add(new Transaction(
                txnIdSeq.incrementAndGet(),
                accountNo,
                "INTEREST",
                interest,
                new Timestamp(System.currentTimeMillis()),
                acc.getBalance()
            ));
        }
        return interest;
    }

    // --- FIXED DEPOSITS (NEW FEATURE) ---

    public int createFixedDeposit(int accountNo, double principal, double rate, int months) throws SQLException, InsufficientFundsException {
        Account acc = getAccount(accountNo);
        if (acc == null) {
            throw new IllegalArgumentException("Account #" + accountNo + " does not exist.");
        }
        if (principal < 1000.0) {
            throw new IllegalArgumentException("Minimum Fixed Deposit principal amount is Rs. 1,000.00.");
        }
        if (months < 3) {
            throw new IllegalArgumentException("Minimum Fixed Deposit tenure is 3 months.");
        }

        // Debit principal from account
        acc.withdraw(principal);

        // Record investment transaction
        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            accountNo,
            "FD_CREATE",
            principal,
            new Timestamp(System.currentTimeMillis()),
            acc.getBalance()
        ));

        int newFdId = fdIdSeq.incrementAndGet();
        LocalDate now = LocalDate.now();
        FixedDeposit fd = new FixedDeposit(newFdId, accountNo, acc.getCustomerId(), principal, rate, months,
                now, now.plusMonths(months),
                FixedDeposit.calculateMaturityAmount(principal, rate, months), "ACTIVE");
        memFixedDeposits.put(newFdId, fd);
        return newFdId;
    }

    public synchronized double closeFixedDeposit(int fdId) throws SQLException {
        FixedDeposit fd = memFixedDeposits.get(fdId);
        if (fd == null) {
            throw new IllegalArgumentException("Fixed Deposit #" + fdId + " not found.");
        }
        if (!"ACTIVE".equalsIgnoreCase(fd.getStatus())) {
            throw new IllegalStateException("Fixed Deposit #" + fdId + " is already " + fd.getStatus() + ".");
        }

        Account acc = getAccount(fd.getAccountNo());
        if (acc == null) {
            throw new IllegalArgumentException("Linked Account #" + fd.getAccountNo() + " not found.");
        }

        double payout;
        if (LocalDate.now().isAfter(fd.getMaturityDate()) || LocalDate.now().isEqual(fd.getMaturityDate())) {
            payout = fd.getMaturityAmount();
            fd.setStatus("MATURED");
        } else {
            payout = fd.calculatePrematureLiquidationAmount();
            fd.setStatus("PREMATURE_CLOSED");
        }

        acc.deposit(payout);

        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            acc.getAccountNo(),
            "FD_MATURE",
            payout,
            new Timestamp(System.currentTimeMillis()),
            acc.getBalance()
        ));

        return payout;
    }

    public List<Map<String, Object>> getAllFixedDepositsDetailed() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FixedDeposit fd : memFixedDeposits.values()) {
            Customer c = memCustomers.get(fd.getCustomerId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fdId", fd.getFdId());
            map.put("accountNo", fd.getAccountNo());
            map.put("customerId", fd.getCustomerId());
            map.put("customerName", c != null ? c.getName() : "Unknown");
            map.put("principalAmount", fd.getPrincipalAmount());
            map.put("interestRate", fd.getInterestRate());
            map.put("tenureMonths", fd.getTenureMonths());
            map.put("startDate", fd.getStartDate() != null ? fd.getStartDate().toString() : "");
            map.put("maturityDate", fd.getMaturityDate() != null ? fd.getMaturityDate().toString() : "");
            map.put("maturityAmount", fd.getMaturityAmount());
            map.put("status", fd.getStatus());
            list.add(map);
        }
        list.sort((x, y) -> Integer.compare((int) y.get("fdId"), (int) x.get("fdId")));
        return list;
    }

    public List<FixedDeposit> getAccountFixedDeposits(int accountNo) {
        List<FixedDeposit> list = new ArrayList<>();
        for (FixedDeposit fd : memFixedDeposits.values()) {
            if (fd.getAccountNo() == accountNo) {
                list.add(fd);
            }
        }
        return list;
    }

    // --- LOANS & EMI (NEW FEATURES) ---

    // trg_check_loan simulation
    public int applyLoan(Loan loan) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.applyLoan(loan); } catch (SQLException e) {
                if (e.getSQLState() != null && e.getSQLState().equals("45000")) {
                    throw e;
                }
                useMySQL = false;
            }
        }
        if (loan.getAmount() <= 0) {
            throw new SQLException("Loan rejection: Amount must be greater than zero.", "45000");
        }
        boolean hasAccount = memAccounts.values().stream().anyMatch(a -> a.getCustomerId() == loan.getCustomerId());
        if (!hasAccount) {
            throw new SQLException("Loan rejection: Customer has no active account in the bank.", "45000");
        }

        int newLoanId = loanIdSeq.incrementAndGet();
        Loan created = new Loan(newLoanId, loan.getCustomerId(), loan.getBranchId(), loan.getLoanType(), loan.getAmount(), loan.getInterestRate(), "PENDING");
        memLoans.add(created);
        return newLoanId;
    }

    public boolean updateLoanStatus(int loanId, String newStatus) throws SQLException {
        if (useMySQL) {
            return bankDAO.updateLoanStatus(loanId, newStatus);
        }
        for (Loan l : memLoans) {
            if (l.getLoanId() == loanId) {
                // Replace with updated status
                int idx = memLoans.indexOf(l);
                memLoans.set(idx, new Loan(l.getLoanId(), l.getCustomerId(), l.getBranchId(), l.getLoanType(), l.getAmount(), l.getInterestRate(), newStatus.toUpperCase()));
                return true;
            }
        }
        return false;
    }

    // Pay Loan EMI
    public synchronized boolean payLoanEMI(int loanId, int accountNo, double amount) throws SQLException, InsufficientFundsException {
        if (useMySQL) {
            return bankDAO.payLoanEMI(loanId, accountNo, amount);
        }
        Loan targetLoan = null;
        for (Loan l : memLoans) {
            if (l.getLoanId() == loanId) {
                targetLoan = l;
                break;
            }
        }
        if (targetLoan == null) {
            throw new IllegalArgumentException("Loan #" + loanId + " not found.");
        }
        if (!"APPROVED".equalsIgnoreCase(targetLoan.getStatus())) {
            throw new IllegalStateException("Cannot pay towards loan with status: " + targetLoan.getStatus());
        }

        Account acc = getAccount(accountNo);
        if (acc == null) {
            throw new IllegalArgumentException("Account #" + accountNo + " not found.");
        }

        acc.withdraw(amount);

        memTransactions.add(new Transaction(
            txnIdSeq.incrementAndGet(),
            accountNo,
            "LOAN_EMI",
            amount,
            new Timestamp(System.currentTimeMillis()),
            acc.getBalance()
        ));

        double remainingAmount = Math.max(0.0, targetLoan.getAmount() - amount);
        String updatedStatus = (remainingAmount <= 0) ? "CLOSED" : "APPROVED";

        int idx = memLoans.indexOf(targetLoan);
        memLoans.set(idx, new Loan(targetLoan.getLoanId(), targetLoan.getCustomerId(), targetLoan.getBranchId(), targetLoan.getLoanType(), remainingAmount, targetLoan.getInterestRate(), updatedStatus));

        return true;
    }

    public List<Loan> getCustomerLoans(int customerId) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getCustomerLoans(customerId); } catch (SQLException e) { useMySQL = false; }
        }
        List<Loan> list = new ArrayList<>();
        for (Loan l : memLoans) {
            if (l.getCustomerId() == customerId) {
                list.add(l);
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAllLoansDetailed() throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAllLoansDetailed(); } catch (SQLException e) { useMySQL = false; }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Loan l : memLoans) {
            Map<String, Object> map = new HashMap<>();
            Customer c = memCustomers.get(l.getCustomerId());
            Branch b = memBranches.get(l.getBranchId());
            map.put("loanId", l.getLoanId());
            map.put("customerId", l.getCustomerId());
            map.put("customerName", c != null ? c.getName() : "Unknown");
            map.put("branchId", l.getBranchId());
            map.put("branchName", b != null ? b.getBranchName() : "Unknown");
            map.put("loanType", l.getLoanType());
            map.put("amount", l.getAmount());
            map.put("interestRate", l.getInterestRate());
            map.put("status", l.getStatus());
            list.add(map);
        }
        list.sort((x, y) -> Integer.compare((int) y.get("loanId"), (int) x.get("loanId")));
        return list;
    }

    // --- TRANSACTIONS ---

    public List<Transaction> getAllTransactions(int limit) throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getAllTransactions(limit); } catch (SQLException e) { useMySQL = false; }
        }
        List<Transaction> copy;
        synchronized (memTransactions) {
            copy = new ArrayList<>(memTransactions);
        }
        copy.sort((a, b) -> b.getTxnDate().compareTo(a.getTxnDate()));
        int max = limit > 0 ? Math.min(limit, copy.size()) : Math.min(100, copy.size());
        return new ArrayList<>(copy.subList(0, max));
    }

    // Filter transactions by account, type, or limit
    public List<Transaction> filterTransactions(int accountNo, String txnType, int limit) {
        List<Transaction> list = new ArrayList<>();
        synchronized (memTransactions) {
            for (Transaction t : memTransactions) {
                if (accountNo > 0 && t.getAccountNo() != accountNo) continue;
                if (txnType != null && !txnType.isBlank() && !t.getTxnType().equalsIgnoreCase(txnType)) continue;
                list.add(t);
            }
        }
        list.sort((a, b) -> b.getTxnDate().compareTo(a.getTxnDate()));
        int max = limit > 0 ? Math.min(limit, list.size()) : list.size();
        return new ArrayList<>(list.subList(0, max));
    }

    // --- REPORTS & STATS ---

    public List<Map<String, Object>> getBranchReportList() throws SQLException {
        if (useMySQL) {
            try { return bankDAO.getBranchReportList(); } catch (SQLException e) { useMySQL = false; }
        }
        List<Map<String, Object>> report = new ArrayList<>();
        for (Branch b : memBranches.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("branchId", b.getBranchId());
            map.put("branchName", b.getBranchName());
            map.put("ifscCode", b.getIfscCode());
            map.put("city", b.getCity());

            long accCount = memAccounts.values().stream().filter(a -> a.getBranchId() == b.getBranchId()).count();
            double totalBal = memAccounts.values().stream().filter(a -> a.getBranchId() == b.getBranchId()).mapToDouble(Account::getBalance).sum();

            map.put("totalAccounts", (int) accCount);
            map.put("totalBalance", totalBal);
            report.add(map);
        }
        report.sort(Comparator.comparingInt(m -> (int) m.get("branchId")));
        return report;
    }

    public Map<String, Object> getDashboardStats() throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        List<AccountDetail> accounts = getAllAccountsDetailed();
        List<Customer> customers = getAllCustomers();
        List<Branch> branches = getAllBranches();
        List<Map<String, Object>> loans = getAllLoansDetailed();
        List<Map<String, Object>> fds = getAllFixedDepositsDetailed();

        double totalDeposits = accounts.stream().mapToDouble(AccountDetail::getBalance).sum();
        double totalLoanAmount = loans.stream().filter(l -> "APPROVED".equals(l.get("status"))).mapToDouble(l -> ((Number) l.get("amount")).doubleValue()).sum();
        double totalFdAmount = fds.stream().filter(f -> "ACTIVE".equals(f.get("status"))).mapToDouble(f -> ((Number) f.get("principalAmount")).doubleValue()).sum();

        stats.put("totalAccounts", accounts.size());
        stats.put("totalCustomers", customers.size());
        stats.put("totalBranches", branches.size());
        stats.put("totalLoans", loans.size());
        stats.put("totalFixedDeposits", fds.size());
        stats.put("totalDeposits", totalDeposits);
        stats.put("totalLoanAmount", totalLoanAmount);
        stats.put("totalFdInvestments", totalFdAmount);
        stats.put("databaseMode", useMySQL ? "MySQL (bankdb)" : "InMemory Simulation");
        stats.put("isMySQL", useMySQL);
        return stats;
    }
}
