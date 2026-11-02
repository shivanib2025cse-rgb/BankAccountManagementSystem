import java.time.LocalDate;
import java.util.*;

// UTILITY: Lightweight JSON serializer & parser with zero external dependencies
public class JsonUtil {

    public static String toJson(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof String || obj instanceof Character) {
            return "\"" + escapeString(obj.toString()) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof LocalDate || obj instanceof java.util.Date || obj instanceof java.sql.Date || obj instanceof java.sql.Timestamp) {
            return "\"" + obj.toString() + "\"";
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(java.lang.reflect.Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }

        // Domain models mapping
        if (obj instanceof Customer c) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("customerId", c.getCustomerId());
            map.put("name", c.getName());
            map.put("phone", c.getPhone());
            map.put("email", c.getEmail());
            map.put("address", c.getAddress());
            return toJson(map);
        }
        if (obj instanceof Branch b) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("branchId", b.getBranchId());
            map.put("branchName", b.getBranchName());
            map.put("ifscCode", b.getIfscCode());
            map.put("city", b.getCity());
            return toJson(map);
        }
        if (obj instanceof Account a) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("accountNo", a.getAccountNo());
            map.put("customerId", a.getCustomerId());
            map.put("branchId", a.getBranchId());
            map.put("accountType", a.getType());
            map.put("balance", a.getBalance());
            map.put("status", a.getStatus());
            map.put("openedOn", a.getOpenedOn() != null ? a.getOpenedOn().toString() : "");
            return toJson(map);
        }
        if (obj instanceof AccountDetail ad) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("accountNo", ad.getAccountNo());
            map.put("customerId", ad.getCustomerId());
            map.put("customerName", ad.getCustomerName());
            map.put("phone", ad.getPhone());
            map.put("email", ad.getEmail());
            map.put("branchId", ad.getBranchId());
            map.put("branchName", ad.getBranchName());
            map.put("ifscCode", ad.getIfscCode());
            map.put("accountType", ad.getAccountType());
            map.put("balance", ad.getBalance());
            map.put("status", ad.getStatus());
            map.put("openedOn", ad.getOpenedOn() != null ? ad.getOpenedOn().toString() : "");
            return toJson(map);
        }
        if (obj instanceof FixedDeposit fd) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fdId", fd.getFdId());
            map.put("accountNo", fd.getAccountNo());
            map.put("customerId", fd.getCustomerId());
            map.put("principalAmount", fd.getPrincipalAmount());
            map.put("interestRate", fd.getInterestRate());
            map.put("tenureMonths", fd.getTenureMonths());
            map.put("startDate", fd.getStartDate() != null ? fd.getStartDate().toString() : "");
            map.put("maturityDate", fd.getMaturityDate() != null ? fd.getMaturityDate().toString() : "");
            map.put("maturityAmount", fd.getMaturityAmount());
            map.put("status", fd.getStatus());
            return toJson(map);
        }
        if (obj instanceof Loan l) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("loanId", l.getLoanId());
            map.put("customerId", l.getCustomerId());
            map.put("branchId", l.getBranchId());
            map.put("loanType", l.getLoanType());
            map.put("amount", l.getAmount());
            map.put("interestRate", l.getInterestRate());
            map.put("status", l.getStatus());
            return toJson(map);
        }
        if (obj instanceof Transaction t) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("txnId", t.getTxnId());
            map.put("accountNo", t.getAccountNo());
            map.put("txnType", t.getTxnType());
            map.put("amount", t.getAmount());
            map.put("txnDate", t.getTxnDate() != null ? t.getTxnDate().toString() : "");
            map.put("balanceAfter", t.getBalanceAfter());
            return toJson(map);
        }

        return "\"" + escapeString(obj.toString()) + "\"";
    }

    public static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // Parses a simple JSON object into Map<String, Object>
    public static Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null) return result;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        s = s.trim();
        if (s.isEmpty()) return result;

        int pos = 0;
        int len = s.length();

        while (pos < len) {
            // Skip whitespace and commas
            while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= len) break;

            // Expect key starting with quote
            if (s.charAt(pos) != '"') {
                // Key without quotes or end
                int nextColon = s.indexOf(':', pos);
                if (nextColon == -1) break;
                String rawKey = s.substring(pos, nextColon).trim();
                pos = nextColon + 1;
                Object val = parseValue(s, pos);
                result.put(rawKey, val);
                pos = advancePastValue(s, pos);
                continue;
            }

            // Quoted key
            pos++; // Skip opening quote
            StringBuilder keyBuilder = new StringBuilder();
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '\\' && pos + 1 < len) {
                    pos++;
                    keyBuilder.append(s.charAt(pos));
                } else if (c == '"') {
                    pos++;
                    break;
                } else {
                    keyBuilder.append(c);
                }
                pos++;
            }
            String key = keyBuilder.toString();

            // Find colon
            while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ':')) {
                pos++;
            }
            if (pos >= len) {
                result.put(key, null);
                break;
            }

            // Parse value
            Object value = parseValue(s, pos);
            result.put(key, value);
            pos = advancePastValue(s, pos);
        }

        return result;
    }

    private static Object parseValue(String s, int start) {
        int pos = start;
        int len = s.length();
        while (pos < len && Character.isWhitespace(s.charAt(pos))) pos++;
        if (pos >= len) return null;

        char first = s.charAt(pos);
        if (first == '"') {
            // String
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '\\' && pos + 1 < len) {
                    pos++;
                    char esc = s.charAt(pos);
                    switch (esc) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(esc);
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
                pos++;
            }
            return sb.toString();
        }

        // Object or array or primitive
        StringBuilder token = new StringBuilder();
        int braceCount = 0;
        int bracketCount = 0;
        boolean inQuotes = false;

        while (pos < len) {
            char c = s.charAt(pos);
            if (c == '"' && (pos == 0 || s.charAt(pos - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
                else if (c == ',' && braceCount == 0 && bracketCount == 0) {
                    break;
                }
            }
            token.append(c);
            pos++;
        }

        String raw = token.toString().trim();
        if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (raw.equalsIgnoreCase("null")) return null;

        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            } else {
                return Long.parseLong(raw);
            }
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private static int advancePastValue(String s, int start) {
        int pos = start;
        int len = s.length();
        while (pos < len && Character.isWhitespace(s.charAt(pos))) pos++;
        if (pos >= len) return len;

        char first = s.charAt(pos);
        if (first == '"') {
            pos++;
            while (pos < len) {
                if (s.charAt(pos) == '\\' && pos + 1 < len) {
                    pos += 2;
                    continue;
                }
                if (s.charAt(pos) == '"') {
                    pos++;
                    break;
                }
                pos++;
            }
            // Skip trailing comma
            while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) {
                if (s.charAt(pos) == ',') { pos++; break; }
                pos++;
            }
            return pos;
        }

        int braceCount = 0;
        int bracketCount = 0;
        boolean inQuotes = false;
        while (pos < len) {
            char c = s.charAt(pos);
            if (c == '"' && (pos == 0 || s.charAt(pos - 1) != '\\')) inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
                else if (c == ',' && braceCount == 0 && bracketCount == 0) {
                    pos++; // consume comma
                    break;
                }
            }
            pos++;
        }
        return pos;
    }

    public static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : defaultVal;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString().trim()); } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    public static double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString().trim()); } catch (Exception ignored) {}
        }
        return defaultVal;
    }
}
