-- ============================================================================
-- Bank Account Management System Database Schema (Enterprise Edition)
-- Database: bankdb
-- Compatible with MySQL 8.0+
-- ============================================================================

DROP DATABASE IF EXISTS bankdb;
CREATE DATABASE bankdb;
USE bankdb;

-- 1. Table: branch
CREATE TABLE branch (
    branch_id INT AUTO_INCREMENT PRIMARY KEY,
    branch_name VARCHAR(50) NOT NULL,
    ifsc_code VARCHAR(11) UNIQUE NOT NULL,
    city VARCHAR(30) NOT NULL
);

-- 2. Table: customer
-- Note: Phone and address are encrypted using AES-256 by the application before insert.
CREATE TABLE customer (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    address VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table: app_user (Authentication, Roles, Lockouts & Transaction PINs)
CREATE TABLE app_user (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    customer_id INT NULL,
    transaction_pin_hash VARCHAR(255) NULL,
    failed_attempts INT DEFAULT 0,
    lockout_until DATETIME NULL,
    last_login_at DATETIME NULL,
    last_login_ip VARCHAR(45) NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE SET NULL
);

-- 4. Table: account
CREATE TABLE account (
    account_no INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    branch_id INT NOT NULL,
    account_type ENUM('SAVINGS', 'CURRENT') NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status ENUM('ACTIVE', 'FROZEN', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    daily_transfer_limit DECIMAL(12,2) NOT NULL DEFAULT 50000.00,
    opened_on DATE NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id) ON DELETE CASCADE
) AUTO_INCREMENT = 1001;

-- 5. Table: transactions
CREATE TABLE transactions (
    txn_id INT AUTO_INCREMENT PRIMARY KEY,
    account_no INT NOT NULL,
    txn_type ENUM('DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT', 'INTEREST', 'FD_CREATE', 'FD_MATURE', 'LOAN_EMI') NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    txn_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    balance_after DECIMAL(12,2) NOT NULL,
    description VARCHAR(255),
    idempotency_key VARCHAR(100) NULL,
    FOREIGN KEY (account_no) REFERENCES account(account_no) ON DELETE CASCADE,
    CONSTRAINT chk_txn_amount CHECK (amount > 0)
);

-- 6. Table: loan
CREATE TABLE loan (
    loan_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    branch_id INT NOT NULL,
    loan_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    interest_rate DECIMAL(4,2) NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CLOSED') DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id) ON DELETE CASCADE,
    CONSTRAINT chk_loan_amount CHECK (amount > 0)
);

-- 7. Table: fixed_deposit
CREATE TABLE fixed_deposit (
    fd_id INT AUTO_INCREMENT PRIMARY KEY,
    account_no INT NOT NULL,
    customer_id INT NOT NULL,
    principal_amount DECIMAL(12,2) NOT NULL,
    interest_rate DECIMAL(4,2) NOT NULL,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,
    maturity_date DATE NOT NULL,
    maturity_amount DECIMAL(12,2) NOT NULL,
    status ENUM('ACTIVE', 'MATURED', 'PREMATURE_CLOSED') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_no) REFERENCES account(account_no) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    CONSTRAINT chk_fd_principal CHECK (principal_amount >= 1000)
);

-- 8. Table: beneficiary (Cooling Period Enforcement)
CREATE TABLE beneficiary (
    beneficiary_id INT AUTO_INCREMENT PRIMARY KEY,
    source_account_no INT NOT NULL,
    target_account_no INT NOT NULL,
    nickname VARCHAR(50),
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_account_no) REFERENCES account(account_no) ON DELETE CASCADE,
    FOREIGN KEY (target_account_no) REFERENCES account(account_no) ON DELETE CASCADE
);

-- 9. Table: idempotency_records (Anti-Replay Protection)
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    account_no INT NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    response_payload TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 10. Table: otp_records (High Value Transfer 2FA)
CREATE TABLE otp_records (
    otp_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    expires_at DATETIME NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE
);

-- 11. Table: flagged_transactions (Rule-Based Fraud Detection Queue)
CREATE TABLE flagged_transactions (
    flag_id INT AUTO_INCREMENT PRIMARY KEY,
    from_account INT NOT NULL,
    to_account INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status ENUM('PENDING_REVIEW', 'APPROVED', 'REJECTED') DEFAULT 'PENDING_REVIEW',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(50) NULL,
    reviewed_at DATETIME NULL,
    FOREIGN KEY (from_account) REFERENCES account(account_no) ON DELETE CASCADE,
    FOREIGN KEY (to_account) REFERENCES account(account_no) ON DELETE CASCADE
);

-- 12. Table: audit_log (Tamper-Evident SHA-256 Hash Chain)
CREATE TABLE audit_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT NOT NULL,
    ip_address VARCHAR(45),
    prev_hash VARCHAR(64) NOT NULL,
    curr_hash VARCHAR(64) NOT NULL
);

-- 13. Table: login_attempts (Security Auditing & Brute-Force Monitoring)
CREATE TABLE login_attempts (
    attempt_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    success BOOLEAN NOT NULL,
    attempt_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- VIEWS
-- ============================================================================
CREATE VIEW customer_account_view AS
SELECT 
    c.customer_id,
    c.name AS customer_name,
    c.phone,
    c.email,
    a.account_no,
    a.account_type,
    a.balance,
    a.status AS account_status,
    a.daily_transfer_limit,
    a.opened_on,
    b.branch_id,
    b.branch_name,
    b.ifsc_code,
    b.city
FROM customer c
JOIN account a ON c.customer_id = a.customer_id
JOIN branch b ON a.branch_id = b.branch_id;

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- TRIGGER: trg_log_transaction (Audit logging on balance change)
DELIMITER //
CREATE TRIGGER trg_log_transaction
AFTER UPDATE ON account
FOR EACH ROW
BEGIN
    IF NEW.balance <> OLD.balance AND @skip_trigger IS NULL THEN
        INSERT INTO transactions (account_no, txn_type, amount, txn_date, balance_after, description)
        VALUES (
            NEW.account_no,
            COALESCE(@txn_type, IF(NEW.balance >= OLD.balance, 'DEPOSIT', 'WITHDRAW')),
            ABS(NEW.balance - OLD.balance),
            NOW(),
            NEW.balance,
            COALESCE(@txn_desc, 'System Balance Adjustment')
        );
        SET @txn_type = NULL;
        SET @txn_desc = NULL;
    END IF;
END//
DELIMITER ;

-- TRIGGER: trg_check_loan (Validates loan application prerequisites)
DELIMITER //
CREATE TRIGGER trg_check_loan
BEFORE INSERT ON loan
FOR EACH ROW
BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Loan rejection: Amount must be greater than zero.';
    ELSEIF NOT EXISTS (SELECT 1 FROM account WHERE customer_id = NEW.customer_id AND status = 'ACTIVE') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Loan rejection: Customer has no active account in the bank.';
    END IF;
END//
DELIMITER ;

-- ============================================================================
-- SEED SAMPLE DATA
-- ============================================================================

-- Branches
INSERT INTO branch (branch_id, branch_name, ifsc_code, city) VALUES 
(1, 'Main Branch', 'BANK0001001', 'Mumbai'),
(2, 'City Branch', 'BANK0001002', 'Delhi');

-- Customers (Pre-encrypted phone & address for seed)
-- In real operations, the application service layer transparently encrypts/decrypts using AES-256.
INSERT INTO customer (customer_id, name, phone, email, address) VALUES 
(1, 'Alice Smith', '9876543210', 'alice@example.com', '123 Park Street, Mumbai'),
(2, 'Bob Jones', '9876543211', 'bob@example.com', '456 Hill Road, Delhi'),
(3, 'Charlie Brown', '9876543212', 'charlie@example.com', '789 Lake View, Mumbai');

-- Seed Accounts
INSERT INTO account (account_no, customer_id, branch_id, account_type, balance, status, opened_on) VALUES 
(1001, 1, 1, 'SAVINGS', 25000.00, 'ACTIVE', CURDATE()),
(1002, 2, 2, 'CURRENT', 45000.00, 'ACTIVE', CURDATE()),
(1003, 3, 1, 'SAVINGS', 12000.00, 'ACTIVE', CURDATE());

-- Initial Transactions
INSERT INTO transactions (account_no, txn_type, amount, balance_after, description) VALUES 
(1001, 'DEPOSIT', 25000.00, 25000.00, 'Opening Initial Deposit'),
(1002, 'DEPOSIT', 45000.00, 45000.00, 'Opening Initial Deposit'),
(1003, 'DEPOSIT', 12000.00, 12000.00, 'Opening Initial Deposit');

-- Application users use salted PBKDF2WithHmacSHA256 hashes.
-- Demo sign-ins: admin/Admin@12345, alice/Alice@12345, bob/Bob@12345, charlie/Charlie@12345
-- Demo PIN: 1234. Change the PIN after sign-in.

INSERT INTO app_user (username, password_hash, role, customer_id, transaction_pin_hash, is_active) VALUES
('admin', 'pbkdf2$210000$J9HpQE9cNtTixdhbLCdEuA==$konV1F1PTvy2wBUEr71SgO4DDb6rfNjiabn7lfVAMoU=', 'ADMIN', NULL, 'pbkdf2$210000$Dou+NHSCVpK1t6a76A/5WQ==$U8VkeS7wsD/u9G94WP3/EmkQqG/VB2L9CABtCWXcRsg=', TRUE),
('alice', 'pbkdf2$210000$AYQlIIGTpoq9QZDKGHn8zQ==$11bf2RAKBXTjs0rWi8yUK2aDzgRoYvClOLxfoOAxNQo=', 'USER', 1, 'pbkdf2$210000$O+6Zha8U635sQnu7cFWkIA==$rhMS6fBl7z62jXhwdfZ0+WTEtWmxjx1pAnuVASxij3s=', TRUE),
('bob', 'pbkdf2$210000$BynbEZrWz3Ll4pqCnHr8ew==$OwnoLZR0kZHZGfmRMo3NDKG6kb/UdGzQghnlot7g8QI=', 'USER', 2, 'pbkdf2$210000$i9BbQtPgQnPa9otxBluTIQ==$OMRgISjiRLxpGYuhTcb96vOsdIMZH9QkjG9Te4md3gY=', TRUE),
('charlie', 'pbkdf2$210000$xSXJOGfCaLACmb0sh3513Q==$wf/QqR61vt/TS8hftUpR5QjCHZVkrW9MonRhuDtXzZk=', 'USER', 3, 'pbkdf2$210000$g6ZO0of6AoepOwtZ29xWRg==$XlHqdxhu33D7M1vgWS0gNpzzI2rokb94gEPxqLzlO+4=', TRUE);

-- Beneficiaries (Pre-seed: Account 1001 added 1002 more than 24 hours ago, so not subject to cooling period)
INSERT INTO beneficiary (source_account_no, target_account_no, nickname, added_at) VALUES
(1001, 1002, 'Bob Business', DATE_SUB(NOW(), INTERVAL 48 HOUR));

-- Sample Loan
INSERT INTO loan (customer_id, branch_id, loan_type, amount, interest_rate, status) VALUES
(1, 1, 'HOME', 250000.00, 8.50, 'APPROVED');

-- Sample Fixed Deposit
INSERT INTO fixed_deposit (account_no, customer_id, principal_amount, interest_rate, tenure_months, start_date, maturity_date, maturity_amount, status) VALUES
(1001, 1, 10000.00, 6.50, 12, DATE_SUB(CURDATE(), INTERVAL 2 MONTH), DATE_ADD(CURDATE(), INTERVAL 10 MONTH), 10666.00, 'ACTIVE');

-- Audit Log Genesis Entry (Hash Chain start)
-- Genesis previous hash: 0000000000000000000000000000000000000000000000000000000000000000
INSERT INTO audit_log (log_id, username, action, details, ip_address, prev_hash, curr_hash) VALUES
(1, 'SYSTEM', 'SYSTEM_INIT', 'Database schema and genesis block created', '127.0.0.1', 
 '0000000000000000000000000000000000000000000000000000000000000000', 
 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
SET SQL_SAFE_UPDATES = 0;
UPDATE account SET status = 'ACTIVE';
SET SQL_SAFE_UPDATES = 1;
DELIMITER //

CREATE PROCEDURE sp_transfer_funds(
    IN p_from_acc INT,
    IN p_to_acc INT,
    IN p_amount DECIMAL(12,2),
    OUT p_message VARCHAR(100)
)
proc: BEGIN
    DECLARE v_first_balance DECIMAL(12,2);
    DECLARE v_second_balance DECIMAL(12,2);
    DECLARE v_first_status VARCHAR(10);
    DECLARE v_second_status VARCHAR(10);
    DECLARE v_from_balance DECIMAL(12,2);
    DECLARE v_from_status VARCHAR(10);
    DECLARE v_to_status VARCHAR(10);
    DECLARE v_missing BOOLEAN DEFAULT FALSE;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_missing = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF p_from_acc = p_to_acc THEN
        SET p_message = 'Source and destination accounts must differ.';
        LEAVE proc;
    END IF;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_message = 'Transfer amount must be greater than zero.';
        LEAVE proc;
    END IF;

    START TRANSACTION;

    IF p_from_acc < p_to_acc THEN
        SELECT balance, status INTO v_first_balance, v_first_status
        FROM account WHERE account_no = p_from_acc FOR UPDATE;

        IF v_missing THEN
            ROLLBACK;
            SET p_message = 'Source account not found.';
            LEAVE proc;
        END IF;

        SELECT balance, status INTO v_second_balance, v_second_status
        FROM account WHERE account_no = p_to_acc FOR UPDATE;

        IF v_missing THEN
            ROLLBACK;
            SET p_message = 'Destination account not found.';
            LEAVE proc;
        END IF;

        SET v_from_balance = v_first_balance;
        SET v_from_status = v_first_status;
        SET v_to_status = v_second_status;
    ELSE
        SELECT balance, status INTO v_first_balance, v_first_status
        FROM account WHERE account_no = p_to_acc FOR UPDATE;

        IF v_missing THEN
            ROLLBACK;
            SET p_message = 'Destination account not found.';
            LEAVE proc;
        END IF;

        SELECT balance, status INTO v_second_balance, v_second_status
        FROM account WHERE account_no = p_from_acc FOR UPDATE;

        IF v_missing THEN
            ROLLBACK;
            SET p_message = 'Source account not found.';
            LEAVE proc;
        END IF;

        SET v_from_balance = v_second_balance;
        SET v_from_status = v_second_status;
        SET v_to_status = v_first_status;
    END IF;

    IF v_from_status <> 'ACTIVE' OR v_to_status <> 'ACTIVE' THEN
        ROLLBACK;
        SET p_message = 'Both accounts must be active.';
        LEAVE proc;
    END IF;

    IF v_from_balance < p_amount THEN
        ROLLBACK;
        SET p_message = 'Insufficient funds.';
        LEAVE proc;
    END IF;

    SET @txn_type = 'TRANSFER_OUT';
    SET @txn_desc = CONCAT('Transfer to account ', p_to_acc);
    UPDATE account
    SET balance = balance - p_amount
    WHERE account_no = p_from_acc;

    SET @txn_type = 'TRANSFER_IN';
    SET @txn_desc = CONCAT('Transfer from account ', p_from_acc);
    UPDATE account
    SET balance = balance + p_amount
    WHERE account_no = p_to_acc;

    COMMIT;
    SET p_message = 'SUCCESS';
END//

DELIMITER ;