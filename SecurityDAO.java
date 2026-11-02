import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Authentication, transaction PIN, and tamper-evident audit persistence. */
public class SecurityDAO {
    private static final int PBKDF2_ROUNDS = 210_000;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static class LoginResult {
        public final boolean success;
        public final String token, username, role, lastLoginAt, lastLoginIp, error;
        public final int userId;
        LoginResult(boolean success, String token, String username, String role, int userId,
                    String lastLoginAt, String lastLoginIp, String error) {
            this.success=success; this.token=token; this.username=username; this.role=role; this.userId=userId;
            this.lastLoginAt=lastLoginAt; this.lastLoginIp=lastLoginIp; this.error=error;
        }
    }

    // Security: PBKDF2 with a unique salt slows offline password cracking after credential database theft.
    public static String hashSecret(String value) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] hash = derive(value.toCharArray(), salt, PBKDF2_ROUNDS);
        return "pbkdf2$" + PBKDF2_ROUNDS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }
    private static byte[] derive(char[] value, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(value, salt, rounds, HASH_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Password hashing unavailable", e); }
        finally { spec.clearPassword(); }
    }
    // Security: Constant-time comparison prevents timing analysis from revealing password or PIN hash matches.
    public static boolean verifySecret(String value, String encoded) {
        if (value == null || encoded == null) return false;
        try { String[] p=encoded.split("\\$"); if(p.length!=4||!p[0].equals("pbkdf2"))return false;
            return MessageDigest.isEqual(derive(value.toCharArray(),Base64.getDecoder().decode(p[2]),Integer.parseInt(p[1])),Base64.getDecoder().decode(p[3]));
        } catch(RuntimeException e){return false;}
    }

    // Security: Five failed attempts trigger a 15-minute database lockout and every attempt records source IP and time.
    public LoginResult login(String username,String password,String ip) throws SQLException {
        String role=null,oldAt=null,oldIp=null; int id=0;
        LocalDateTime now=LocalDateTime.now(); boolean valid=false;
        try(Connection c=DBConnection.getConnection()) { c.setAutoCommit(false); try {
            String hash=null; int failures=0; Timestamp locked=null; boolean active=false;
            // Security: Locking the user row serializes failed-login counters so parallel guesses cannot bypass lockout.
            try(PreparedStatement p=c.prepareStatement("SELECT user_id,password_hash,role,failed_attempts,lockout_until,last_login_at,last_login_ip,is_active FROM app_user WHERE username=? FOR UPDATE")) {
                p.setString(1,username); try(ResultSet r=p.executeQuery()) { if(r.next()){id=r.getInt(1);hash=r.getString(2);role=r.getString(3);failures=r.getInt(4);locked=r.getTimestamp(5);oldAt=r.getString(6);oldIp=r.getString(7);active=r.getBoolean(8);} }
            }
            boolean isLocked=locked!=null&&locked.toLocalDateTime().isAfter(now);
            valid=!isLocked&&active&&hash!=null&&verifySecret(password,hash);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO login_attempts(username,ip_address,success,attempt_time) VALUES(?,?,?,?)")) {
                p.setString(1,username==null?"":username);p.setString(2,ip);p.setBoolean(3,valid);p.setTimestamp(4,Timestamp.valueOf(now));p.executeUpdate();
            }
            if(id>0) {
                if(valid) try(PreparedStatement p=c.prepareStatement("UPDATE app_user SET failed_attempts=0,lockout_until=NULL,last_login_at=?,last_login_ip=? WHERE user_id=?")) {
                    p.setTimestamp(1,Timestamp.valueOf(now));p.setString(2,ip);p.setInt(3,id);p.executeUpdate();
                } else {
                    failures++;
                    try(PreparedStatement p=c.prepareStatement("UPDATE app_user SET failed_attempts=?,lockout_until=? WHERE user_id=?")) {
                        p.setInt(1,failures);if(failures>=5)p.setTimestamp(2,Timestamp.valueOf(now.plusMinutes(15)));else p.setNull(2,Types.TIMESTAMP);p.setInt(3,id);p.executeUpdate();
                    }
                }
            }
            c.commit();
        } catch(Exception e) { c.rollback(); if(e instanceof SQLException)throw(SQLException)e;throw new SQLException(e); } }
        if(!valid)return new LoginResult(false,null,null,null,0,null,null,"Invalid credentials");
        return new LoginResult(true,SessionStore.create(id,username,role),username,role,id,oldAt,oldIp,null);
    }
    public void setPin(int userId,String pin)throws SQLException{try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE app_user SET transaction_pin_hash=? WHERE user_id=?")){p.setString(1,hashSecret(pin));p.setInt(2,userId);if(p.executeUpdate()!=1)throw new SQLException("User not found");}}
    // Security: A separate salted PIN hash prevents a stolen active session from authorizing withdrawals or transfers.
    public boolean verifyPin(int userId,String pin)throws SQLException{try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT transaction_pin_hash FROM app_user WHERE user_id=?")){p.setInt(1,userId);try(ResultSet r=p.executeQuery()){return r.next()&&verifySecret(pin,r.getString(1));}}}

    // Security: A SHA-256 hash chain makes unauthorized audit edits, reordering, and deletions detectable.
    public void audit(String username,String action,String details,String ip)throws SQLException{
        try(Connection c=DBConnection.getConnection()){c.setAutoCommit(false);try{String prev="0".repeat(64);
            try(PreparedStatement p=c.prepareStatement("SELECT curr_hash FROM audit_log ORDER BY log_id DESC LIMIT 1 FOR UPDATE");ResultSet r=p.executeQuery()){if(r.next())prev=r.getString(1);}
            Timestamp now=Timestamp.valueOf(LocalDateTime.now());String curr=sha256(now+"|"+username+"|"+action+"|"+details+"|"+ip+"|"+prev);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_log(timestamp,username,action,details,ip_address,prev_hash,curr_hash) VALUES(?,?,?,?,?,?,?)")){p.setTimestamp(1,now);p.setString(2,username);p.setString(3,action);p.setString(4,details);p.setString(5,ip);p.setString(6,prev);p.setString(7,curr);p.executeUpdate();}c.commit();
        }catch(Exception e){c.rollback();if(e instanceof SQLException)throw(SQLException)e;throw new SQLException(e);}}
    }
    private static String sha256(String value){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:h)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
final class SessionStore {
    static final class Session { final int userId;final String username,role;volatile long lastActivity;Session(int i,String u,String r){userId=i;username=u;role=r;lastActivity=System.currentTimeMillis();} }
    private static final java.util.concurrent.ConcurrentHashMap<String,Session> S=new java.util.concurrent.ConcurrentHashMap<>();
    static String create(int i,String u,String r){String t=java.util.UUID.randomUUID().toString()+java.util.UUID.randomUUID().toString();S.put(t,new Session(i,u,r));return t;}
    static Session get(String t){Session s=S.get(t);if(s!=null&&System.currentTimeMillis()-s.lastActivity>600000){S.remove(t);return null;}return s;}
    static void touch(String t){Session s=S.get(t);if(s!=null)s.lastActivity=System.currentTimeMillis();}
    static void remove(String t){S.remove(t);}
}
