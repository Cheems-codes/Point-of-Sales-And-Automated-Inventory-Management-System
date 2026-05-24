import java.sql.*;

public class DatabaseManager {

    private static final String URL =
    "jdbc:sqlserver://DESKTOP-JBMSR4N\\SQLEXPRESS;databaseName=pos_system;" +
    "integratedSecurity=true;trustServerCertificate=true;";

    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL);
        }
        return connection;
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("[DB] Could not close connection: " + e.getMessage());
        }
    }

    // ── PRODUCTS ──────────────────────────────────────────────

    public static void saveProduct(String id, Product p) {
        String sql = """
            IF EXISTS (SELECT 1 FROM products WHERE id = ?)
                UPDATE products SET stock = ?, last_restocked = ? WHERE id = ?
            ELSE
                INSERT INTO products (id, name, category, stock, par_level, price, expiry_date, last_restocked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setInt(2, p.stock);
            ps.setString(3, p.lastRestocked);
            ps.setString(4, id);
            ps.setString(5, id);
            ps.setString(6, p.name);
            ps.setString(7, p.category);
            ps.setInt(8, p.stock);
            ps.setInt(9, p.parLevel);
            ps.setDouble(10, p.price);
            ps.setString(11, p.expiryDate);
            ps.setString(12, p.lastRestocked);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveProduct error: " + e.getMessage());
        }
    }

    public static void updateStock(String productId, int newStock, String lastRestocked) {
        String sql = "UPDATE products SET stock = ?, last_restocked = ? WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, lastRestocked);
            ps.setString(3, productId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] updateStock error: " + e.getMessage());
        }
    }

    // ── ORDERS ────────────────────────────────────────────────

    public static int saveOrder(double subtotal, Discount discount, Payment payment,
                                double discountAmount, double total) {
        String sql = """
            INSERT INTO orders
              (subtotal, discount_type, discount_id_number, discount_amount,
               total, payment_method, account_info, cash_tendered, change_amount)
            OUTPUT INSERTED.order_id
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setDouble(1, subtotal);
            ps.setString(2, discount != null ? discount.getType() : null);
            ps.setString(3, discount != null ? discount.idNumber : null);
            ps.setDouble(4, discountAmount);
            ps.setDouble(5, total);
            ps.setString(6, payment.getMethod());
            ps.setString(7, payment instanceof CashPayment ? null : payment.accountInfo);
            ps.setObject(8, payment instanceof CashPayment ? ((CashPayment) payment).amountTendered : null);
            ps.setObject(9, payment instanceof CashPayment ? ((CashPayment) payment).change : null);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("[DB] saveOrder error: " + e.getMessage());
        }
        return -1;
    }

    public static void saveOrderItem(int orderId, String productId, Product p, int qty) {
        String sql = """
            INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, productId);
            ps.setString(3, p.name);
            ps.setInt(4, qty);
            ps.setDouble(5, p.price);
            ps.setDouble(6, p.price * qty);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveOrderItem error: " + e.getMessage());
        }
    }

    // ── RESTOCK LOG ───────────────────────────────────────────

    public static void saveRestockLog(String productId, String productName,
                                      int added, int oldStock, int newStock, String role) {
        String sql = """
            INSERT INTO restock_log (product_id, product_name, quantity_added, old_stock, new_stock, restocked_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, productId);
            ps.setString(2, productName);
            ps.setInt(3, added);
            ps.setInt(4, oldStock);
            ps.setInt(5, newStock);
            ps.setString(6, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveRestockLog error: " + e.getMessage());
        }
    }

    // ── TIME LOG ──────────────────────────────────────────────

    public static void saveTimeLog(String role, String name, String action) {
        String sql = "INSERT INTO time_log (staff_role, staff_name, action, log_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, name);
            ps.setString(3, action);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveTimeLog error: " + e.getMessage());
        }
    }

    // ── AUDIT TRAIL ───────────────────────────────────────────

    public static void saveAuditEvent(String eventType, String details) {
        String sql = "INSERT INTO audit_trail (event_type, details) VALUES (?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] saveAuditEvent error: " + e.getMessage());
        }
    }
}