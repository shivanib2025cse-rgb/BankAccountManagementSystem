import java.sql.SQLException;
import java.util.List;
import java.util.Map;

// INTEGRATION & LOGIC TEST: Validates business logic, polymorphism, triggers, and JSON utility
public class BankTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   RUNNING BANK ACCOUNT MANAGEMENT SYSTEM TESTS  ");
        System.out.println("=================================================");

        int passed = 0;
        int failed = 0;

        BankDataService service = new BankDataService();

        // TEST 1: Load initial seed data
        try {
            List<Customer> customers = service.getAllCustomers();
            List<Branch> branches = service.getAllBranches();
            List<AccountDetail> accounts = service.getAllAccountsDetailed();

            assertCondition(customers.size() >= 3, "Customer count >= 3 (Found: " + customers.size() + ")");
            assertCondition(branches.size() >= 2, "Branch count >= 2 (Found: " + branches.size() + ")");
            assertCondition(accounts.size() >= 3, "Account count >= 3 (Found: " + accounts.size() + ")");
            System.out.println("✅ [PASS] Test 1: Seed data initialization");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 1: Seed data initialization - " + t.getMessage());
            failed++;
        }

        // TEST 2: Deposit logic & transaction logging
        try {
            Account accBefore = service.getAccount(1001);
            double balBefore = accBefore.getBalance();
            boolean ok = service.deposit(1001, 1500.0);
            Account accAfter = service.getAccount(1001);

            assertCondition(ok, "Deposit returned true");
            assertCondition(accAfter.getBalance() == balBefore + 1500.0, "Balance updated correctly: " + accAfter.getBalance());

            List<Transaction> txns = service.getMiniStatement(1001);
            assertCondition(!txns.isEmpty() && txns.get(0).getTxnType().equals("DEPOSIT"), "Transaction ledger logged via trigger");
            System.out.println("✅ [PASS] Test 2: Deposit & trigger transaction logging");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 2: Deposit test - " + t.getMessage());
            failed++;
        }

        // TEST 3: Polymorphic Withdrawal & Minimum Balance Rule (Savings Account)
        try {
            Account acc = service.getAccount(1001);
            assertCondition(acc instanceof SavingsAccount, "Account 1001 is SavingsAccount");

            // Valid withdrawal
            service.withdraw(1001, 500.0);

            // Attempt withdrawal violating Rs. 1000 minimum balance
            double excessAmount = acc.getBalance() - 500.0; // Leaves less than 1000
            boolean threw = false;
            try {
                service.withdraw(1001, excessAmount);
            } catch (InsufficientFundsException e) {
                threw = true;
            }
            assertCondition(threw, "SavingsAccount correctly threw InsufficientFundsException for minimum balance");
            System.out.println("✅ [PASS] Test 3: Polymorphic SavingsAccount withdrawal rule");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 3: Savings withdrawal rule - " + t.getMessage());
            failed++;
        }

        // TEST 4: Polymorphic Overdraft Limit (Current Account)
        try {
            Account acc = service.getAccount(1002);
            assertCondition(acc instanceof CurrentAccount, "Account 1002 is CurrentAccount");

            // Current account can withdraw past balance down to -10,000
            double currentBal = acc.getBalance();
            service.withdraw(1002, currentBal + 2000.0); // Now in Rs. 2000 overdraft
            assertCondition(acc.getBalance() == -2000.0, "CurrentAccount allowed overdraft: " + acc.getBalance());

            // Exceeding Rs. 10,000 overdraft limit should throw
            boolean threw = false;
            try {
                service.withdraw(1002, 9000.0); // Would be -11,000
            } catch (InsufficientFundsException e) {
                threw = true;
            }
            assertCondition(threw, "CurrentAccount correctly rejected withdrawal exceeding Rs. 10,000 overdraft");
            System.out.println("✅ [PASS] Test 4: Polymorphic CurrentAccount overdraft rule");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 4: Current overdraft rule - " + t.getMessage());
            failed++;
        }

        // TEST 5: Atomic Fund Transfer (sp_transfer_funds simulation)
        try {
            double fromBalBefore = service.getAccount(1001).getBalance();
            double toBalBefore = service.getAccount(1003).getBalance();

            String result = service.transferFunds(1001, 1003, 1000.0);
            assertCondition(result.startsWith("SUCCESS"), "Transfer succeeded: " + result);

            double fromBalAfter = service.getAccount(1001).getBalance();
            double toBalAfter = service.getAccount(1003).getBalance();

            assertCondition(fromBalAfter == fromBalBefore - 1000.0, "Source debited correctly");
            assertCondition(toBalAfter == toBalBefore + 1000.0, "Target credited correctly");
            System.out.println("✅ [PASS] Test 5: Atomic fund transfer (sp_transfer_funds)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 5: Fund transfer test - " + t.getMessage());
            failed++;
        }

        // TEST 6: Polymorphic Yearly Interest Calculation
        try {
            // Savings account earns 4% interest
            double bal = service.getAccount(1003).getBalance();
            double expectedInterest = bal * 0.04;
            double interest = service.applyYearlyInterest(1003);
            assertCondition(Math.abs(interest - expectedInterest) < 0.01, "Savings earned 4% interest: " + interest);

            // Current account earns 0% interest
            double curInterest = service.applyYearlyInterest(1002);
            assertCondition(curInterest == 0.0, "Current account earned 0% interest");
            System.out.println("✅ [PASS] Test 6: Polymorphic interest calculation");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 6: Polymorphic interest test - " + t.getMessage());
            failed++;
        }

        // TEST 7: Loan Trigger Validation (trg_check_loan)
        try {
            // Rejection test: Customer 999 has no account
            Loan badLoan = new Loan(999, 1, "PERSONAL", 50000.0, 10.0);
            boolean rejectedNoAcc = false;
            try {
                service.applyLoan(badLoan);
            } catch (SQLException e) {
                rejectedNoAcc = e.getMessage().contains("Customer has no active account");
            }
            assertCondition(rejectedNoAcc, "trg_check_loan rejected loan for customer without account");

            // Rejection test: Amount <= 0
            Loan zeroLoan = new Loan(1, 1, "PERSONAL", 0.0, 10.0);
            boolean rejectedZero = false;
            try {
                service.applyLoan(zeroLoan);
            } catch (SQLException e) {
                rejectedZero = e.getMessage().contains("Amount must be greater than zero");
            }
            assertCondition(rejectedZero, "trg_check_loan rejected loan with zero amount");

            // Successful loan for Customer 1 who has active account
            Loan goodLoan = new Loan(1, 1, "HOME", 100000.0, 8.5);
            int loanId = service.applyLoan(goodLoan);
            assertCondition(loanId > 0, "Valid loan accepted with generated ID: " + loanId);
            System.out.println("✅ [PASS] Test 7: trg_check_loan trigger validation");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 7: Loan trigger test - " + t.getMessage());
            failed++;
        }

        // TEST 8: JSON Serialization & Parsing (JsonUtil)
        try {
            Map<String, Object> testMap = Map.of("name", "Alice", "balance", 5000.5, "active", true);
            String json = JsonUtil.toJson(testMap);
            assertCondition(json.contains("\"name\":\"Alice\""), "JSON serialization of map contains name");

            Map<String, Object> parsed = JsonUtil.parseJsonObject("{\"name\": \"Alice\", \"balance\": 5000.5, \"active\": true}");
            assertCondition("Alice".equals(JsonUtil.getString(parsed, "name", "")), "Parsed name matches");
            assertCondition(JsonUtil.getDouble(parsed, "balance", 0.0) == 5000.5, "Parsed balance matches");
            System.out.println("✅ [PASS] Test 8: JsonUtil serialization & parsing");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 8: JsonUtil test - " + t.getMessage());
            failed++;
        }

        // TEST 9: Branch SQL GROUP BY Aggregate Report
        try {
            List<Map<String, Object>> report = service.getBranchReportList();
            assertCondition(!report.isEmpty(), "Branch report is not empty");
            assertCondition((int) report.get(0).get("totalAccounts") >= 1, "Branch has aggregate accounts");
            System.out.println("✅ [PASS] Test 9: SQL GROUP BY Branch report");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 9: Branch report test - " + t.getMessage());
            failed++;
        }

        // TEST 10: Fixed Deposit (FD) Creation & Compounding Maturity
        try {
            Account acc = service.getAccount(1001);
            double balBefore = acc.getBalance();
            int fdId = service.createFixedDeposit(1001, 2000.0, 6.5, 12);
            assertCondition(fdId > 0, "Fixed Deposit created with ID: " + fdId);
            assertCondition(acc.getBalance() == balBefore - 2000.0, "Principal debited from account");

            double expectedMaturity = FixedDeposit.calculateMaturityAmount(2000.0, 6.5, 12);
            assertCondition(expectedMaturity > 2000.0, "Compound interest calculated: " + expectedMaturity);

            // Liquidation test
            double payout = service.closeFixedDeposit(fdId);
            assertCondition(payout >= 2000.0, "Payout credited: " + payout);
            assertCondition(acc.getBalance() == balBefore - 2000.0 + payout, "Payout reflected in account balance");
            System.out.println("✅ [PASS] Test 10: Fixed Deposit creation & compound maturity calculation");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 10: Fixed Deposit test - " + t.getMessage());
            failed++;
        }

        // TEST 11: Account Freeze & Status Enforcement
        try {
            service.updateAccountStatus(1003, "FROZEN");
            Account acc = service.getAccount(1003);
            assertCondition("FROZEN".equals(acc.getStatus()), "Account status is FROZEN");

            // Attempt deposit on frozen account
            boolean depositBlocked = false;
            try {
                service.deposit(1003, 500.0);
            } catch (IllegalStateException e) {
                depositBlocked = e.getMessage().contains("FROZEN");
            }
            assertCondition(depositBlocked, "Deposit blocked on frozen account");

            // Attempt withdrawal on frozen account
            boolean withdrawBlocked = false;
            try {
                service.withdraw(1003, 500.0);
            } catch (IllegalStateException e) {
                withdrawBlocked = e.getMessage().contains("FROZEN");
            }
            assertCondition(withdrawBlocked, "Withdrawal blocked on frozen account");

            // Unfreeze account
            service.updateAccountStatus(1003, "ACTIVE");
            assertCondition("ACTIVE".equals(service.getAccount(1003).getStatus()), "Account unfrozen back to ACTIVE");
            System.out.println("✅ [PASS] Test 11: Account freeze & status enforcement");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 11: Account freeze test - " + t.getMessage());
            failed++;
        }

        // TEST 12: Loan Status Lifecycle & EMI Repayment
        try {
            Loan loan = new Loan(1, 1, "CAR", 50000.0, 9.0);
            int loanId = service.applyLoan(loan);
            service.updateLoanStatus(loanId, "APPROVED");

            double balBefore = service.getAccount(1001).getBalance();
            boolean paid = service.payLoanEMI(loanId, 1001, 2000.0);
            assertCondition(paid, "EMI repayment processed");
            assertCondition(service.getAccount(1001).getBalance() == balBefore - 2000.0, "EMI debited from account");

            List<Loan> custLoans = service.getCustomerLoans(1);
            Loan updatedLoan = custLoans.stream().filter(l -> l.getLoanId() == loanId).findFirst().orElse(null);
            assertCondition(updatedLoan != null && updatedLoan.getAmount() == 48000.0, "Remaining loan reduced to 48,000");
            System.out.println("✅ [PASS] Test 12: Loan lifecycle & EMI repayment");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 12: Loan EMI repayment test - " + t.getMessage());
            failed++;
        }

        // TEST 13: Customer 360° Financial Portfolio
        try {
            Map<String, Object> portfolio = service.getCustomerPortfolio(1);
            assertCondition(portfolio != null, "Portfolio returned");
            assertCondition(portfolio.containsKey("customer"), "Portfolio contains customer");
            assertCondition(portfolio.containsKey("accounts"), "Portfolio contains accounts");
            assertCondition(portfolio.containsKey("fixedDeposits"), "Portfolio contains FDs");
            assertCondition(portfolio.containsKey("loans"), "Portfolio contains loans");
            assertCondition(portfolio.containsKey("netWorth"), "Portfolio calculates net worth");
            System.out.println("✅ [PASS] Test 13: Customer 360° portfolio");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [FAIL] Test 13: Customer portfolio test - " + t.getMessage());
            failed++;
        }

        System.out.println("=================================================");
        System.out.println("   TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("=================================================");
        if (failed > 0) System.exit(1);
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }
}
