import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

// BACKEND REST API & STATIC WEB SERVER: High-performance embedded HTTP server for Bank Account Management System
public class BankServer {

    private static final int DEFAULT_PORT = 8080;
    private static Path WEB_DIR;
    private static BankDataService dataService;
    private static final SecurityDAO securityDAO = new SecurityDAO();

    static {
        Path localWeb = Paths.get("web").toAbsolutePath();
        if (Files.exists(localWeb)) {
            WEB_DIR = localWeb;
        } else {
            Path projectWeb = Paths.get("C:/shivani/BankAccountManagementSystem/web").toAbsolutePath();
            if (Files.exists(projectWeb)) {
                WEB_DIR = projectWeb;
            } else {
                WEB_DIR = localWeb;
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=================================================");
        System.out.println("   BANK ACCOUNT MANAGEMENT SYSTEM - WEB BACKEND  ");
        System.out.println("=================================================");

        dataService = new BankDataService();

        HttpServer server = null;
        for (int p = port; p < port + 10; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                port = p;
                break;
            } catch (IOException e) {
                System.out.println("[BankServer] Port " + p + " is occupied or restricted, trying next...");
            }
        }

        if (server == null) {
            System.err.println("[BankServer] Failed to bind to any port between " + port + " and " + (port + 10));
            System.exit(1);
        }

        server.createContext("/api", new ApiHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();

        System.out.println("\n🚀 Server is actively running!");
        System.out.println("👉 Local Web UI URL  : http://localhost:" + port);
        System.out.println("👉 REST API Root     : http://localhost:" + port + "/api");
        System.out.println("👉 Storage Mode      : " + (dataService.isUsingMySQL() ? "MySQL 8 (bankdb)" : "InMemory Fallback"));
        System.out.println("👉 Serving Web Root  : " + WEB_DIR);
        System.out.println("\nPress Ctrl+C to terminate the server.\n");
    }

    // --- API HANDLER ---

    static class ApiHandler implements HttpHandler {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startTime = System.currentTimeMillis();
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            // Handle CORS Preflight
            if ("OPTIONS".equals(method)) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            try {
                processRequest(exchange, method, path);
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", e.getMessage() != null ? e.getMessage() : "Internal server error");
                sendJsonResponse(exchange, 500, err);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                System.out.printf("[%s] %-6s %-32s -> %d (%dms)%n",
                        LocalDateTime.now().format(TIME_FMT),
                        method,
                        path,
                        exchange.getResponseCode(),
                        duration);
            }
        }

        private void processRequest(HttpExchange exchange, String method, String path) throws Exception {
            // Strip trailing slash
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (path.equals("/api/auth/login") && "POST".equals(method)) {
                Map<String,Object> req=JsonUtil.parseJsonObject(readBody(exchange));
                String username=JsonUtil.getString(req,"username","").trim();
                String password=JsonUtil.getString(req,"password","");
                SecurityDAO.LoginResult login=securityDAO.login(username,password,clientIp);
                securityDAO.audit(username,login.success?"LOGIN_SUCCESS":"LOGIN_FAILURE","Login attempt",clientIp);
                if(!login.success){sendError(exchange,401,"Invalid credentials");return;}
                Map<String,Object> response=new LinkedHashMap<>();response.put("success",true);response.put("token",login.token);
                response.put("username",login.username);response.put("role",login.role);response.put("userId",login.userId);
                response.put("lastLoginAt",login.lastLoginAt);response.put("lastLoginIp",login.lastLoginIp);
                sendJsonResponse(exchange,200,response);return;
            }
            if (path.equals("/api/auth/register") && "POST".equals(method)) {
                Map<String,Object> req=JsonUtil.parseJsonObject(readBody(exchange));
                String username=JsonUtil.getString(req,"username","").trim(), password=JsonUtil.getString(req,"password","");
                int customerId=JsonUtil.getInt(req,"customerId",0);
                if(!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")||username.length()<4||customerId<1){sendError(exchange,400,"Username, valid customer ID, or strong password is invalid.");return;}
                try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO app_user(username,password_hash,role,customer_id) VALUES(?,?,'USER',?)")){
                    p.setString(1,username);p.setString(2,SecurityDAO.hashSecret(password));p.setInt(3,customerId);p.executeUpdate();
                    securityDAO.audit(username,"REGISTER","Customer account linked",clientIp);
                    sendJsonResponse(exchange,201,Map.of("success",true,"message","Registration complete. Sign in to continue."));
                } catch(java.sql.SQLIntegrityConstraintViolationException e){sendError(exchange,409,"Username or customer already registered.");}
                return;
            }

            String authorization=exchange.getRequestHeaders().getFirst("Authorization");
            String sessionToken=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7):"";
            SessionStore.Session session=SessionStore.get(sessionToken);
            if(path.equals("/api/auth/logout")&&"POST".equals(method)){
                if(session!=null){securityDAO.audit(session.username,"LOGOUT","User signed out",clientIp);SessionStore.remove(sessionToken);}
                sendJsonResponse(exchange,200,Map.of("success",true));return;
            }
            if(path.equals("/api/auth/set-pin")&&"POST".equals(method)){
                if(session==null){sendError(exchange,401,"Sign in required");return;}
                Map<String,Object> req=JsonUtil.parseJsonObject(readBody(exchange));String pin=JsonUtil.getString(req,"pin","");
                if(!pin.matches("^[0-9]{4}$")){sendError(exchange,400,"PIN must be exactly 4 digits.");return;}
                securityDAO.setPin(session.userId,pin);securityDAO.audit(session.username,"PIN_SET","Transaction PIN changed",clientIp);
                sendJsonResponse(exchange,200,Map.of("success",true,"message","Transaction PIN saved"));return;
            }
            if(path.equals("/api/auth/pin-status")&&"GET".equals(method)){
                if(session==null){sendError(exchange,401,"Sign in required");return;}
                boolean hasPin;
                try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT transaction_pin_hash IS NOT NULL FROM app_user WHERE user_id=?")){
                    p.setInt(1,session.userId);try(ResultSet r=p.executeQuery()){hasPin=r.next()&&r.getBoolean(1);}
                }
                sendJsonResponse(exchange,200,Map.of("hasPin",hasPin));return;
            }
            if(!path.equals("/api/health")&&session==null){sendError(exchange,401,"Sign in required");return;}
            if(session!=null) SessionStore.touch(sessionToken);

            // Route: /api/health
            if (path.equals("/api/health")) {
                if ("POST".equals(method)) {
                    dataService.checkConnection();
                }
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("status", "UP");
                res.put("isMySQL", dataService.isUsingMySQL());
                res.put("databaseMode", dataService.isUsingMySQL() ? "MySQL 8 (bankdb)" : "InMemory Fallback");
                sendJsonResponse(exchange, 200, res);
                return;
            }

            // Route: /api/stats
            if (path.equals("/api/stats") && "GET".equals(method)) {
                sendJsonResponse(exchange, 200, dataService.getDashboardStats());
                return;
            }

            // Route: /api/customers
            if (path.equals("/api/customers")) {
                if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 200, dataService.getAllCustomers());
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, Object> req = JsonUtil.parseJsonObject(body);
                    String name = JsonUtil.getString(req, "name", "");
                    String phone = JsonUtil.getString(req, "phone", "");
                    String email = JsonUtil.getString(req, "email", "");
                    String address = JsonUtil.getString(req, "address", "");

                    if (name.isEmpty() || phone.isEmpty()) {
                        sendError(exchange, 400, "Customer Name and Phone Number are required.");
                        return;
                    }

                    try {
                        Customer cust = new Customer(name, phone, email, address);
                        int id = dataService.addCustomer(cust);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("customerId", id);
                        res.put("message", "Customer registered successfully with ID: " + id);
                        sendJsonResponse(exchange, 201, res);
                    } catch (SQLException e) {
                        sendError(exchange, 400, e.getMessage().contains("Duplicate") ? "Phone number is already registered." : e.getMessage());
                    }
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Route: /api/customers/{id}
            if (path.matches("^/api/customers/\\d+$") && "GET".equals(method)) {
                int custId = Integer.parseInt(path.substring("/api/customers/".length()));
                Customer cust = dataService.getCustomerById(custId);
                if (cust == null) {
                    sendError(exchange, 404, "Customer #" + custId + " not found.");
                    return;
                }
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("customer", cust);
                res.put("loans", dataService.getCustomerLoans(custId));
                sendJsonResponse(exchange, 200, res);
                return;
            }

            // Route: /api/branches
            if (path.equals("/api/branches")) {
                if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 200, dataService.getAllBranches());
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, Object> req = JsonUtil.parseJsonObject(body);
                    String name = JsonUtil.getString(req, "branchName", "");
                    String ifsc = JsonUtil.getString(req, "ifscCode", "");
                    String city = JsonUtil.getString(req, "city", "");

                    if (name.isEmpty() || ifsc.isEmpty()) {
                        sendError(exchange, 400, "Branch Name and IFSC Code are required.");
                        return;
                    }

                    try {
                        Branch branch = new Branch(name, ifsc, city);
                        int id = dataService.addBranch(branch);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("branchId", id);
                        res.put("message", "Branch registered successfully with ID: " + id);
                        sendJsonResponse(exchange, 201, res);
                    } catch (SQLException e) {
                        sendError(exchange, 400, e.getMessage().contains("Duplicate") ? "IFSC Code is already assigned." : e.getMessage());
                    }
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Route: /api/branches/report
            if (path.equals("/api/branches/report") && "GET".equals(method)) {
                sendJsonResponse(exchange, 200, dataService.getBranchReportList());
                return;
            }

            // Route: /api/accounts
            if (path.equals("/api/accounts")) {
                if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 200, dataService.getAllAccountsDetailed());
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, Object> req = JsonUtil.parseJsonObject(body);
                    int custId = JsonUtil.getInt(req, "customerId", 0);
                    int branchId = JsonUtil.getInt(req, "branchId", 0);
                    String type = JsonUtil.getString(req, "accountType", "SAVINGS").toUpperCase();
                    double balance = JsonUtil.getDouble(req, "initialBalance", 0.0);

                    if (custId <= 0 || branchId <= 0) {
                        sendError(exchange, 400, "Valid Customer ID and Branch ID are required.");
                        return;
                    }
                    if (balance < 0) {
                        sendError(exchange, 400, "Opening balance cannot be negative.");
                        return;
                    }
                    if ("SAVINGS".equals(type) && balance < 1000.0) {
                        sendError(exchange, 400, "Savings Account requires a minimum opening deposit of Rs. 1000.00.");
                        return;
                    }

                    Account account;
                    if ("SAVINGS".equals(type)) {
                        account = new SavingsAccount(0, custId, branchId, balance, LocalDate.now());
                    } else {
                        account = new CurrentAccount(0, custId, branchId, balance, LocalDate.now());
                    }

                    try {
                        int accNo = dataService.openAccount(account);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("accountNo", accNo);
                        res.put("message", type + " Account opened successfully! Account No: " + accNo);
                        sendJsonResponse(exchange, 201, res);
                    } catch (SQLException e) {
                        sendError(exchange, 400, e.getMessage());
                    }
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Route: /api/accounts/{accNo}
            if (path.matches("^/api/accounts/\\d+$") && "GET".equals(method)) {
                int accNo = Integer.parseInt(path.substring("/api/accounts/".length()));
                AccountDetail ad = dataService.getAccountDetail(accNo);
                if (ad == null) {
                    sendError(exchange, 404, "Account #" + accNo + " not found.");
                    return;
                }
                sendJsonResponse(exchange, 200, ad);
                return;
            }

            // Route: /api/accounts/{accNo}/status
            if (path.matches("^/api/accounts/\\d+/status$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int accNo = Integer.parseInt(parts[3]);
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                String newStatus = JsonUtil.getString(req, "status", "ACTIVE").toUpperCase();

                boolean ok = dataService.updateAccountStatus(accNo, newStatus);
                if (ok) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("accountNo", accNo);
                    res.put("status", newStatus);
                    res.put("message", "Account #" + accNo + " status updated to " + newStatus);
                    sendJsonResponse(exchange, 200, res);
                } else {
                    sendError(exchange, 404, "Account #" + accNo + " not found.");
                }
                return;
            }

            // Route: /api/customers/{id}/portfolio
            if (path.matches("^/api/customers/\\d+/portfolio$") && "GET".equals(method)) {
                int custId = Integer.parseInt(path.substring("/api/customers/".length(), path.indexOf("/portfolio")));
                Map<String, Object> portfolio = dataService.getCustomerPortfolio(custId);
                if (portfolio == null) {
                    sendError(exchange, 404, "Customer #" + custId + " not found.");
                    return;
                }
                sendJsonResponse(exchange, 200, portfolio);
                return;
            }

            // Route: /api/customers/{id}/update
            if (path.matches("^/api/customers/\\d+/update$") && "POST".equals(method)) {
                int custId = Integer.parseInt(path.substring("/api/customers/".length(), path.indexOf("/update")));
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                String name = JsonUtil.getString(req, "name", "");
                String phone = JsonUtil.getString(req, "phone", "");
                String email = JsonUtil.getString(req, "email", "");
                String address = JsonUtil.getString(req, "address", "");

                boolean ok = dataService.updateCustomer(custId, name, phone, email, address);
                if (ok) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("message", "Customer profile updated successfully.");
                    sendJsonResponse(exchange, 200, res);
                } else {
                    sendError(exchange, 404, "Customer #" + custId + " not found.");
                }
                return;
            }

            // Route: /api/loans/{id}/status
            if (path.matches("^/api/loans/\\d+/status$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int loanId = Integer.parseInt(parts[3]);
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                String newStatus = JsonUtil.getString(req, "status", "APPROVED").toUpperCase();

                boolean ok = dataService.updateLoanStatus(loanId, newStatus);
                if (ok) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("loanId", loanId);
                    res.put("status", newStatus);
                    res.put("message", "Loan #" + loanId + " status updated to " + newStatus);
                    sendJsonResponse(exchange, 200, res);
                } else {
                    sendError(exchange, 404, "Loan #" + loanId + " not found.");
                }
                return;
            }

            // Route: /api/loans/{id}/pay (EMI Payment)
            if (path.matches("^/api/loans/\\d+/pay$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int loanId = Integer.parseInt(parts[3]);
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                int accountNo = JsonUtil.getInt(req, "accountNo", 0);
                double amount = JsonUtil.getDouble(req, "amount", 0.0);

                if (accountNo <= 0 || amount <= 0) {
                    sendError(exchange, 400, "Valid Account Number and positive EMI amount are required.");
                    return;
                }

                try {
                    boolean ok = dataService.payLoanEMI(loanId, accountNo, amount);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("message", "EMI payment of Rs. " + String.format("%.2f", amount) + " debited from Account #" + accountNo + " successfully.");
                    sendJsonResponse(exchange, 200, res);
                } catch (Exception e) {
                    sendError(exchange, 400, e.getMessage());
                }
                return;
            }

            // Route: /api/fds (Fixed Deposits)
            if (path.equals("/api/fds")) {
                if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 200, dataService.getAllFixedDepositsDetailed());
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, Object> req = JsonUtil.parseJsonObject(body);
                    int accountNo = JsonUtil.getInt(req, "accountNo", 0);
                    double principal = JsonUtil.getDouble(req, "principalAmount", 0.0);
                    double rate = JsonUtil.getDouble(req, "interestRate", 6.5);
                    int months = JsonUtil.getInt(req, "tenureMonths", 12);

                    if (accountNo <= 0 || principal <= 0) {
                        sendError(exchange, 400, "Valid Account No and Principal Amount are required.");
                        return;
                    }

                    try {
                        int fdId = dataService.createFixedDeposit(accountNo, principal, rate, months);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("fdId", fdId);
                        res.put("message", "Fixed Deposit certificate created successfully! FD #" + fdId);
                        sendJsonResponse(exchange, 201, res);
                    } catch (Exception e) {
                        sendError(exchange, 400, e.getMessage());
                    }
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Route: /api/fds/{id}/close
            if (path.matches("^/api/fds/\\d+/close$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int fdId = Integer.parseInt(parts[3]);

                try {
                    double payout = dataService.closeFixedDeposit(fdId);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("fdId", fdId);
                    res.put("payoutAmount", payout);
                    res.put("message", "Fixed Deposit #" + fdId + " closed. Payout of Rs. " + String.format("%.2f", payout) + " credited to linked account.");
                    sendJsonResponse(exchange, 200, res);
                } catch (Exception e) {
                    sendError(exchange, 400, e.getMessage());
                }
                return;
            }

            // Route: /api/transactions/csv
            if (path.equals("/api/transactions/csv") && "GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                int accNo = 0;
                if (query != null && query.contains("accountNo=")) {
                    try {
                        for (String param : query.split("&")) {
                            if (param.startsWith("accountNo=")) {
                                accNo = Integer.parseInt(param.split("=")[1]);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                List<Transaction> txns = accNo > 0 ? dataService.getMiniStatement(accNo) : dataService.getAllTransactions(500);
                StringBuilder csv = new StringBuilder("Transaction ID,Account No,Type,Amount (Rs.),Balance After (Rs.),Date\n");
                for (Transaction t : txns) {
                    csv.append(t.getTxnId()).append(",")
                       .append(t.getAccountNo()).append(",")
                       .append(t.getTxnType()).append(",")
                       .append(String.format(Locale.US, "%.2f", t.getAmount())).append(",")
                       .append(String.format(Locale.US, "%.2f", t.getBalanceAfter())).append(",")
                       .append("\"").append(t.getTxnDate()).append("\"\n");
                }

                byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"transactions_" + (accNo > 0 ? "acc_" + accNo : "all") + ".csv\"");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, csvBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(csvBytes);
                }
                return;
            }

            // Route: /api/accounts/{accNo}/deposit
            if (path.matches("^/api/accounts/\\d+/deposit$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int accNo = Integer.parseInt(parts[3]);
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                double amount = JsonUtil.getDouble(req, "amount", 0.0);
                if (amount <= 0) {
                    sendError(exchange, 400, "Deposit amount must be greater than zero.");
                    return;
                }

                try {
                    boolean ok = dataService.deposit(accNo, amount);
                    if (ok) {
                        Account acc = dataService.getAccount(accNo);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("accountNo", accNo);
                        res.put("depositedAmount", amount);
                        res.put("newBalance", acc != null ? acc.getBalance() : 0.0);
                        res.put("message", "Rs. " + String.format("%.2f", amount) + " deposited successfully into Account #" + accNo);
                        sendJsonResponse(exchange, 200, res);
                    } else {
                        sendError(exchange, 404, "Account #" + accNo + " not found.");
                    }
                } catch (Exception e) {
                    sendError(exchange, 400, e.getMessage());
                }
                return;
            }

            // Route: /api/accounts/{accNo}/withdraw
            if (path.matches("^/api/accounts/\\d+/withdraw$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int accNo = Integer.parseInt(parts[3]);
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                double amount = JsonUtil.getDouble(req, "amount", 0.0);
                String pin = JsonUtil.getString(req, "transactionPin", "");

                if (!pin.matches("^[0-9]{4}$") || !securityDAO.verifyPin(session.userId,pin)) {
                    sendError(exchange, 403, "Invalid transaction PIN.");
                    return;
                }

                if (amount <= 0) {
                    sendError(exchange, 400, "Withdrawal amount must be greater than zero.");
                    return;
                }

                try {
                    boolean ok = dataService.withdraw(accNo, amount);
                    if (ok) {
                        securityDAO.audit(session.username,"WITHDRAWAL","Account ending "+(accNo%10000)+", amount "+amount,clientIp);
                        Account acc = dataService.getAccount(accNo);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("accountNo", accNo);
                        res.put("withdrawnAmount", amount);
                        res.put("newBalance", acc != null ? acc.getBalance() : 0.0);
                        res.put("message", "Rs. " + String.format("%.2f", amount) + " withdrawn successfully from Account #" + accNo);
                        sendJsonResponse(exchange, 200, res);
                    } else {
                        sendError(exchange, 404, "Account #" + accNo + " not found.");
                    }
                } catch (InsufficientFundsException | IllegalArgumentException e) {
                    sendError(exchange, 400, e.getMessage());
                } catch (Exception e) {
                    sendError(exchange, 500, e.getMessage());
                }
                return;
            }

            // Route: /api/accounts/{accNo}/interest
            if (path.matches("^/api/accounts/\\d+/interest$") && "POST".equals(method)) {
                String[] parts = path.split("/");
                int accNo = Integer.parseInt(parts[3]);

                try {
                    double interest = dataService.applyYearlyInterest(accNo);
                    if (interest < 0) {
                        sendError(exchange, 404, "Account #" + accNo + " not found.");
                    } else {
                        Account acc = dataService.getAccount(accNo);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("accountNo", accNo);
                        res.put("interestCredited", interest);
                        res.put("newBalance", acc != null ? acc.getBalance() : 0.0);
                        res.put("message", interest > 0
                                ? "Rs. " + String.format("%.2f", interest) + " annual interest credited to Account #" + accNo
                                : "No interest calculated (Current accounts earn 0% interest).");
                        sendJsonResponse(exchange, 200, res);
                    }
                } catch (Exception e) {
                    sendError(exchange, 500, e.getMessage());
                }
                return;
            }

            // Route: /api/accounts/{accNo}/statement
            if (path.matches("^/api/accounts/\\d+/statement$") && "GET".equals(method)) {
                String[] parts = path.split("/");
                int accNo = Integer.parseInt(parts[3]);
                try {
                    List<Transaction> txns = dataService.getMiniStatement(accNo);
                    sendJsonResponse(exchange, 200, txns);
                } catch (Exception e) {
                    sendError(exchange, 500, e.getMessage());
                }
                return;
            }

            // Route: /api/transfer (Stored Procedure)
            if (path.equals("/api/transfer") && "POST".equals(method)) {
                String body = readBody(exchange);
                Map<String, Object> req = JsonUtil.parseJsonObject(body);
                int fromAcc = JsonUtil.getInt(req, "fromAccount", 0);
                int toAcc = JsonUtil.getInt(req, "toAccount", 0);
                double amount = JsonUtil.getDouble(req, "amount", 0.0);
                String pin = JsonUtil.getString(req, "transactionPin", "");

                if (!pin.matches("^[0-9]{4}$") || !securityDAO.verifyPin(session.userId, pin)) {
                    sendError(exchange, 403, "Invalid transaction PIN.");
                    return;
                }

                if (fromAcc <= 0 || toAcc <= 0 || amount <= 0) {
                    sendError(exchange, 400, "Source Account, Destination Account, and positive Amount are required.");
                    return;
                }

                try {
                    String resultMsg = dataService.transferFunds(fromAcc, toAcc, amount);
                    boolean isSuccess = resultMsg.toUpperCase().startsWith("SUCCESS");
                    if(isSuccess) securityDAO.audit(session.username,"TRANSFER","From ending "+(fromAcc%10000)+" to ending "+(toAcc%10000)+", amount "+amount,clientIp);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", isSuccess);
                    res.put("message", resultMsg);
                    sendJsonResponse(exchange, isSuccess ? 200 : 400, res);
                } catch (Exception e) {
                    sendError(exchange, 500, e.getMessage());
                }
                return;
            }

            // Route: /api/loans
            if (path.equals("/api/loans")) {
                if ("GET".equals(method)) {
                    sendJsonResponse(exchange, 200, dataService.getAllLoansDetailed());
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, Object> req = JsonUtil.parseJsonObject(body);
                    int custId = JsonUtil.getInt(req, "customerId", 0);
                    int branchId = JsonUtil.getInt(req, "branchId", 0);
                    String loanType = JsonUtil.getString(req, "loanType", "PERSONAL").toUpperCase();
                    double amount = JsonUtil.getDouble(req, "amount", 0.0);
                    double interestRate = JsonUtil.getDouble(req, "interestRate", 10.0);

                    if (custId <= 0 || branchId <= 0) {
                        sendError(exchange, 400, "Valid Customer ID and Branch ID are required.");
                        return;
                    }

                    try {
                        Loan loan = new Loan(custId, branchId, loanType, amount, interestRate);
                        int loanId = dataService.applyLoan(loan);
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("loanId", loanId);
                        res.put("message", "Loan application submitted successfully with Loan ID: " + loanId);
                        sendJsonResponse(exchange, 201, res);
                    } catch (SQLException e) {
                        // Catches trigger check rejection (trg_check_loan)
                        sendError(exchange, 400, e.getMessage());
                    }
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            // Route: /api/transactions
            if (path.equals("/api/transactions") && "GET".equals(method)) {
                int limit = 25;
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("limit=")) {
                    try {
                        String[] qParts = query.split("=");
                        limit = Integer.parseInt(qParts[1]);
                    } catch (Exception ignored) {}
                }
                sendJsonResponse(exchange, 200, dataService.getAllTransactions(limit));
                return;
            }

            // Unrecognized API Route
            sendError(exchange, 404, "Endpoint not found: " + path);
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    os.write(buf, 0, read);
                }
                return os.toString(StandardCharsets.UTF_8);
            }
        }

        private void sendError(HttpExchange exchange, int code, String msg) throws IOException {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", msg);
            sendJsonResponse(exchange, code, err);
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
            byte[] responseBytes = JsonUtil.toJson(data).getBytes(StandardCharsets.UTF_8);
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private void sendCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
    }

    // --- STATIC FILE HANDLER ---

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/") || pathStr.isEmpty()) {
                pathStr = "/index.html";
            }

            // URL decode path
            pathStr = URLDecoder.decode(pathStr, StandardCharsets.UTF_8);

            // Avoid path traversal
            Path resolved = WEB_DIR.resolve(pathStr.substring(1)).normalize();
            if (!resolved.startsWith(WEB_DIR) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                // If not found, check if index.html can be served (SPA route fallback)
                Path index = WEB_DIR.resolve("index.html");
                if (Files.exists(index)) {
                    serveFile(exchange, index, "text/html; charset=UTF-8");
                    return;
                }
                byte[] notFound = "<h1>404 Not Found</h1><p>Bank Management System web asset not found.</p>".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound);
                }
                return;
            }

            String mimeType = getMimeType(resolved.toString());
            serveFile(exchange, resolved, mimeType);
        }

        private void serveFile(HttpExchange exchange, Path file, String mimeType) throws IOException {
            byte[] data = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
