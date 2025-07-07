import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;

public class RailwayManagementSystem {
    private static final Logger logger = LoggerFactory.getLogger(RailwayManagementSystem.class);
    private static HikariDataSource dataSource;
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final String APP_NAME = "Sumanth Railway Management System";
    
    // Configuration constants
    private static final String DB_URL = "jdbc:mysql://localhost:3306/";
    private static final String DB_NAME = "train_management";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123123";
    
    public static void main(String[] args) {
        try {
            initializeDatabase();
            initializeConnectionPool();
            
            logger.info("Starting {}", APP_NAME);
            System.out.println("\n\n\t\t============================================");
            System.out.println("\t\t      WELCOME TO " + APP_NAME.toUpperCase());
            System.out.println("\t\t============================================\n");
            
            if (!checkUserRegistration()) {
                registerNewUser();
            }
            loginUser();
            
        } catch (Exception e) {
            logger.error("Application error: {}", e.getMessage(), e);
            System.out.println("A critical error occurred. Please contact support.");
        } finally {
            shutdown();
        }
    }
    
    private static void initializeDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
            stmt.execute("USE " + DB_NAME);
            
            // Create tables if they don't exist
            stmt.execute("CREATE TABLE IF NOT EXISTS train_details (" +
                "train_name VARCHAR(255) NOT NULL, " +
                "train_no INT PRIMARY KEY, " +
                "starting_point VARCHAR(255) NOT NULL, " +
                "destination VARCHAR(255) NOT NULL, " +
                "extra_specifications VARCHAR(255), " +
                "seats_available INT NOT NULL)");
                
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "user_id VARCHAR(30) PRIMARY KEY, " +
                "username VARCHAR(30) NOT NULL UNIQUE, " +
                "password VARCHAR(60) NOT NULL, " +  // 60 chars for BCrypt hash
                "full_name VARCHAR(50) NOT NULL, " +
                "phone VARCHAR(15) NOT NULL, " +
                "aadhaar VARCHAR(12) UNIQUE, " +
                "address VARCHAR(100) NOT NULL, " +
                "pincode VARCHAR(6) NOT NULL, " +
                "age INT NOT NULL)");
                
            stmt.execute("CREATE TABLE IF NOT EXISTS reservations (" +
                "reservation_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id VARCHAR(30) NOT NULL, " +
                "train_no INT NOT NULL, " +
                "berth_type VARCHAR(10), " +
                "meals_required BOOLEAN, " +
                "departure_date DATE NOT NULL, " +
                "booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(user_id), " +
                "FOREIGN KEY (train_no) REFERENCES train_details(train_no))");
                
            logger.info("Database initialized successfully");
        }
    }
    
    private static void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL + DB_NAME);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        dataSource = new HikariDataSource(config);
        logger.info("Connection pool initialized");
    }
    
    private static boolean checkUserRegistration() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
             
            rs.next();
            return rs.getInt(1) > 0;
        }
    }
    
    private static void registerNewUser() throws SQLException {
        System.out.println("\n=== NEW USER REGISTRATION ===");
        
        String fullName = getValidInput("Enter your full name: ", 
            input -> input.matches("[A-Za-z ]+"), "Invalid name format");
            
        int age = getValidIntegerInput("Enter your age: ", 15, 120);
        
        String phone = getValidInput("Enter phone number (10 digits): ",
            input -> input.matches("\\d{10}"), "Invalid phone number");
            
        String aadhaar = getValidInput("Enter Aadhaar number (12 digits): ",
            input -> input.matches("\\d{12}"), "Invalid Aadhaar number");
            
        String address = getValidInput("Enter your address: ", 
            input -> !input.trim().isEmpty(), "Address cannot be empty");
            
        String pincode = getValidInput("Enter pincode (6 digits): ",
            input -> input.matches("\\d{6}"), "Invalid pincode");
            
        String username = getValidInput("Choose a username: ",
            input -> input.matches("[A-Za-z0-9_]+"), "Invalid username");
            
        String password = getValidInput("Choose a password (min 8 chars): ",
            input -> input.length() >= 8, "Password too short");
            
        String userId = "USER" + System.currentTimeMillis();
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                 
            pstmt.setString(1, userId);
            pstmt.setString(2, username);
            pstmt.setString(3, hashedPassword);
            pstmt.setString(4, fullName);
            pstmt.setString(5, phone);
            pstmt.setString(6, aadhaar);
            pstmt.setString(7, address);
            pstmt.setString(8, pincode);
            pstmt.setInt(9, age);
            
            pstmt.executeUpdate();
            System.out.println("\nRegistration successful! Your user ID is: " + userId);
            
            // Save to CSV for backup
            saveUserToCSV(userId, username, fullName, age, phone, aadhaar, address, pincode);
        }
    }
    
    private static void loginUser() throws SQLException {
        System.out.println("\n=== USER LOGIN ===");
        int attempts = 0;
        final int MAX_ATTEMPTS = 3;
        
        while (attempts < MAX_ATTEMPTS) {
            String username = getInput("Username: ");
            String password = getInput("Password: ");
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT user_id, password, full_name FROM users WHERE username = ?")) {
                     
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    if (BCrypt.checkpw(password, storedHash)) {
                        String userId = rs.getString("user_id");
                        String fullName = rs.getString("full_name");
                        System.out.println("\nWelcome, " + fullName + "!");
                        showMainMenu(userId, fullName);
                        return;
                    }
                }
                
                attempts++;
                System.out.println("Invalid credentials. Attempts remaining: " + (MAX_ATTEMPTS - attempts));
            }
        }
        
        System.out.println("Maximum login attempts reached. Exiting...");
        System.exit(0);
    }
    
    private static void showMainMenu(String userId, String fullName) throws SQLException {
        while (true) {
            System.out.println("\n=== MAIN MENU ===");
            System.out.println("Logged in as: " + fullName);
            System.out.println("1. Train Management");
            System.out.println("2. Reservation System");
            System.out.println("3. User Profile");
            System.out.println("4. View Patents");
            System.out.println("5. Logout");
            System.out.println("6. Exit");
            
            int choice = getValidIntegerInput("Enter your choice: ", 1, 6);
            
            switch (choice) {
                case 1 -> trainManagementMenu();
                case 2 -> reservationMenu(userId);
                case 3 -> userProfileMenu(userId);
                case 4 -> showPatents();
                case 5 -> { logout(); return; }
                case 6 -> { shutdown(); System.exit(0); }
            }
        }
    }
    
    private static void trainManagementMenu() throws SQLException {
        System.out.println("\n=== TRAIN MANAGEMENT ===");
        System.out.println("1. View All Trains");
        System.out.println("2. Add New Train");
        System.out.println("3. Update Train Details");
        System.out.println("4. Remove Train");
        System.out.println("5. Back to Main Menu");
        
        int choice = getValidIntegerInput("Enter your choice: ", 1, 5);
        
        switch (choice) {
            case 1 -> displayAllTrains();
            case 2 -> addNewTrain();
            case 3 -> updateTrainDetails();
            case 4 -> removeTrain();
            case 5 -> { return; }
        }
    }
    
    private static void displayAllTrains() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM train_details")) {
             
            System.out.println("\n=== AVAILABLE TRAINS ===");
            System.out.println("+---------------------+-----------+----------------+----------------+------------------------+----------------+");
            System.out.println("| Train Name          | Train No  | Starting Point | Destination    | Specifications         | Seats Available|");
            System.out.println("+---------------------+-----------+----------------+----------------+------------------------+----------------+");
            
            while (rs.next()) {
                System.out.printf("| %-19s | %-9d | %-14s | %-14s | %-22s | %-14d |\n",
                    rs.getString("train_name"),
                    rs.getInt("train_no"),
                    rs.getString("starting_point"),
                    rs.getString("destination"),
                    rs.getString("extra_specifications"),
                    rs.getInt("seats_available"));
            }
            System.out.println("+---------------------+-----------+----------------+----------------+------------------------+----------------+");
        }
    }
    
    private static void addNewTrain() throws SQLException {
        System.out.println("\n=== ADD NEW TRAIN ===");
        
        String trainName = getValidInput("Train name: ", 
            input -> !input.trim().isEmpty(), "Name cannot be empty");
            
        int trainNo = getValidIntegerInput("Train number: ", 1, Integer.MAX_VALUE);
        
        String startingPoint = getValidInput("Starting point: ", 
            input -> !input.trim().isEmpty(), "Starting point cannot be empty");
            
        String destination = getValidInput("Destination: ", 
            input -> !input.trim().isEmpty(), "Destination cannot be empty");
            
        int seats = getValidIntegerInput("Seats available: ", 1, Integer.MAX_VALUE);
        String specs = getInput("Extra specifications (optional): ");
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO train_details VALUES (?, ?, ?, ?, ?, ?)")) {
                 
            pstmt.setString(1, trainName);
            pstmt.setInt(2, trainNo);
            pstmt.setString(3, startingPoint);
            pstmt.setString(4, destination);
            pstmt.setString(5, specs);
            pstmt.setInt(6, seats);
            
            pstmt.executeUpdate();
            System.out.println("Train added successfully!");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                System.out.println("Error: Train number already exists!");
            } else {
                throw e;
            }
        }
    }
    
    private static void updateTrainDetails() throws SQLException {
        int trainNo = getValidIntegerInput("Enter train number to update: ", 1, Integer.MAX_VALUE);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT * FROM train_details WHERE train_no = ?")) {
                 
            pstmt.setInt(1, trainNo);
            ResultSet rs = pstmt.executeQuery();
            
            if (!rs.next()) {
                System.out.println("Train not found!");
                return;
            }
            
            System.out.println("\nCurrent Train Details:");
            System.out.println("1. Name: " + rs.getString("train_name"));
            System.out.println("2. Starting Point: " + rs.getString("starting_point"));
            System.out.println("3. Destination: " + rs.getString("destination"));
            System.out.println("4. Seats Available: " + rs.getInt("seats_available"));
            System.out.println("5. Specifications: " + rs.getString("extra_specifications"));
            
            int field = getValidIntegerInput("Which field to update (1-5, 0 to cancel)? ", 0, 5);
            if (field == 0) return;
            
            String updateField = switch (field) {
                case 1 -> "train_name";
                case 2 -> "starting_point";
                case 3 -> "destination";
                case 4 -> "seats_available";
                case 5 -> "extra_specifications";
                default -> throw new IllegalStateException("Unexpected value: " + field);
            };
            
            String newValue;
            if (field == 4) {
                newValue = String.valueOf(getValidIntegerInput("New seats available: ", 0, Integer.MAX_VALUE));
            } else {
                newValue = getValidInput("New value: ", input -> !input.trim().isEmpty(), "Value cannot be empty");
            }
            
            try (PreparedStatement updateStmt = conn.prepareStatement(
                "UPDATE train_details SET " + updateField + " = ? WHERE train_no = ?")) {
                
                if (field == 4) {
                    updateStmt.setInt(1, Integer.parseInt(newValue));
                } else {
                    updateStmt.setString(1, newValue);
                }
                updateStmt.setInt(2, trainNo);
                
                int rows = updateStmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Train details updated successfully!");
                }
            }
        }
    }
    
    private static void removeTrain() throws SQLException {
        int trainNo = getValidIntegerInput("Enter train number to remove: ", 1, Integer.MAX_VALUE);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "DELETE FROM train_details WHERE train_no = ?")) {
                 
            pstmt.setInt(1, trainNo);
            int rows = pstmt.executeUpdate();
            
            if (rows > 0) {
                System.out.println("Train removed successfully!");
            } else {
                System.out.println("Train not found!");
            }
        }
    }
    
    private static void reservationMenu(String userId) throws SQLException {
        System.out.println("\n=== RESERVATION SYSTEM ===");
        System.out.println("1. Make Reservation");
        System.out.println("2. View My Reservations");
        System.out.println("3. Cancel Reservation");
        System.out.println("4. Back to Main Menu");
        
        int choice = getValidIntegerInput("Enter your choice: ", 1, 4);
        
        switch (choice) {
            case 1 -> makeReservation(userId);
            case 2 -> viewReservations(userId);
            case 3 -> cancelReservation(userId);
            case 4 -> { return; }
        }
    }
    
    private static void makeReservation(String userId) throws SQLException {
        displayAllTrains();
        
        int trainNo = getValidIntegerInput("Enter train number: ", 1, Integer.MAX_VALUE);
        
        // Check train exists and has available seats
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT train_name, seats_available FROM train_details WHERE train_no = ?")) {
                 
            pstmt.setInt(1, trainNo);
            ResultSet rs = pstmt.executeQuery();
            
            if (!rs.next()) {
                System.out.println("Train not found!");
                return;
            }
            
            String trainName = rs.getString("train_name");
            int availableSeats = rs.getInt("seats_available");
            
            if (availableSeats <= 0) {
                System.out.println("No seats available on this train!");
                return;
            }
            
            System.out.println("Booking seat on: " + trainName);
            System.out.println("Seats available: " + availableSeats);
            
            String berthType = getValidInput("Berth type (Lower/Upper/Middle/Side): ", 
                input -> Arrays.asList("LOWER", "UPPER", "MIDDLE", "SIDE").contains(input.toUpperCase()),
                "Invalid berth type");
                
            boolean meals = getYesNoInput("Include meals (Y/N)? ");
            LocalDate departureDate = getValidDateInput("Departure date (YYYY-MM-DD): ");
            
            // Start transaction
            conn.setAutoCommit(false);
            try {
                // Insert reservation
                try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO reservations (user_id, train_no, berth_type, meals_required, departure_date) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                    
                    insertStmt.setString(1, userId);
                    insertStmt.setInt(2, trainNo);
                    insertStmt.setString(3, berthType.toUpperCase());
                    insertStmt.setBoolean(4, meals);
                    insertStmt.setDate(5, java.sql.Date.valueOf(departureDate));
                    insertStmt.executeUpdate();
                }
                
                // Update available seats
                try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE train_details SET seats_available = seats_available - 1 WHERE train_no = ?")) {
                    
                    updateStmt.setInt(1, trainNo);
                    updateStmt.executeUpdate();
                }
                
                conn.commit();
                System.out.println("Reservation successful!");
                
                // Generate and display ticket
                generateTicket(userId, trainNo, departureDate);
                
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Reservation failed: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    private static void viewReservations(String userId) throws SQLException {
        System.out.println("\n=== YOUR RESERVATIONS ===");
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT r.reservation_id, t.train_name, r.berth_type, r.meals_required, " +
                 "r.departure_date, r.booking_date " +
                 "FROM reservations r JOIN train_details t ON r.train_no = t.train_no " +
                 "WHERE r.user_id = ? ORDER BY r.departure_date")) {
                 
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (!rs.isBeforeFirst()) {
                System.out.println("No reservations found.");
                return;
            }
            
            System.out.println("+-----+---------------------+-----------+-------+----------------+---------------------+");
            System.out.println("| ID  | Train Name          | Berth     | Meals | Departure Date | Booking Date        |");
            System.out.println("+-----+---------------------+-----------+-------+----------------+---------------------+");
            
            while (rs.next()) {
                System.out.printf("| %-3d | %-19s | %-9s | %-5s | %-14s | %-19s |\n",
                    rs.getInt("reservation_id"),
                    rs.getString("train_name"),
                    rs.getString("berth_type"),
                    rs.getBoolean("meals_required") ? "Yes" : "No",
                    rs.getDate("departure_date").toString(),
                    rs.getTimestamp("booking_date").toString());
            }
            System.out.println("+-----+---------------------+-----------+-------+----------------+---------------------+");
        }
    }
    
    private static void cancelReservation(String userId) throws SQLException {
        viewReservations(userId);
        int reservationId = getValidIntegerInput("Enter reservation ID to cancel (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (reservationId == 0) return;
        
        try (Connection conn = dataSource.getConnection()) {
            // First get the train number from the reservation
            int trainNo;
            try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT train_no FROM reservations WHERE reservation_id = ? AND user_id = ?")) {
                
                pstmt.setInt(1, reservationId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();
                
                if (!rs.next()) {
                    System.out.println("Reservation not found or doesn't belong to you!");
                    return;
                }
                trainNo = rs.getInt("train_no");
            }
            
            // Start transaction
            conn.setAutoCommit(false);
            try {
                // Delete reservation
                try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM reservations WHERE reservation_id = ? AND user_id = ?")) {
                    
                    deleteStmt.setInt(1, reservationId);
                    deleteStmt.setString(2, userId);
                    int rows = deleteStmt.executeUpdate();
                    
                    if (rows == 0) {
                        System.out.println("Reservation not found or doesn't belong to you!");
                        return;
                    }
                }
                
                // Increment available seats
                try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE train_details SET seats_available = seats_available + 1 WHERE train_no = ?")) {
                    
                    updateStmt.setInt(1, trainNo);
                    updateStmt.executeUpdate();
                }
                
                conn.commit();
                System.out.println("Reservation cancelled successfully!");
                
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Cancellation failed: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    private static void userProfileMenu(String userId) throws SQLException {
        System.out.println("\n=== USER PROFILE ===");
        System.out.println("1. View Profile");
        System.out.println("2. Update Profile");
        System.out.println("3. Change Password");
        System.out.println("4. Back to Main Menu");
        
        int choice = getValidIntegerInput("Enter your choice: ", 1, 4);
        
        switch (choice) {
            case 1 -> viewProfile(userId);
            case 2 -> updateProfile(userId);
            case 3 -> changePassword(userId);
            case 4 -> { return; }
        }
    }
    
    private static void viewProfile(String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT * FROM users WHERE user_id = ?")) {
                 
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                System.out.println("\n=== YOUR PROFILE ===");
                System.out.println("User ID: " + rs.getString("user_id"));
                System.out.println("Username: " + rs.getString("username"));
                System.out.println("Full Name: " + rs.getString("full_name"));
                System.out.println("Age: " + rs.getInt("age"));
                System.out.println("Phone: " + rs.getString("phone"));
                System.out.println("Aadhaar: " + rs.getString("aadhaar"));
                System.out.println("Address: " + rs.getString("address"));
                System.out.println("Pincode: " + rs.getString("pincode"));
            }
        }
    }
    
    private static void updateProfile(String userId) throws SQLException {
        viewProfile(userId);
        System.out.println("\nWhich field would you like to update?");
        System.out.println("1. Full Name");
        System.out.println("2. Phone");
        System.out.println("3. Address");
        System.out.println("4. Pincode");
        System.out.println("5. Cancel");
        
        int choice = getValidIntegerInput("Enter your choice: ", 1, 5);
        if (choice == 5) return;
        
        String field = switch (choice) {
            case 1 -> "full_name";
            case 2 -> "phone";
            case 3 -> "address";
            case 4 -> "pincode";
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        };
        
        String newValue;
        if (choice == 2) {
            newValue = getValidInput("New phone (10 digits): ",
                input -> input.matches("\\d{10}"), "Invalid phone number");
        } else if (choice == 4) {
            newValue = getValidInput("New pincode (6 digits): ",
                input -> input.matches("\\d{6}"), "Invalid pincode");
        } else {
            newValue = getValidInput("New value: ", input -> !input.trim().isEmpty(), "Value cannot be empty");
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE users SET " + field + " = ? WHERE user_id = ?")) {
                 
            pstmt.setString(1, newValue);
            pstmt.setString(2, userId);
            
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Profile updated successfully!");
            }
        }
    }
    
    private static void changePassword(String userId) throws SQLException {
        String currentPassword = getInput("Current password: ");
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT password FROM users WHERE user_id = ?")) {
                 
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (!BCrypt.checkpw(currentPassword, storedHash)) {
                    System.out.println("Incorrect current password!");
                    return;
                }
            }
        }
        
        String newPassword = getValidInput("New password (min 8 chars): ",
            input -> input.length() >= 8, "Password too short");
            
        String confirmPassword = getInput("Confirm new password: ");
        
        if (!newPassword.equals(confirmPassword)) {
            System.out.println("Passwords don't match!");
            return;
        }
        
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE users SET password = ? WHERE user_id = ?")) {
                 
            pstmt.setString(1, newHash);
            pstmt.setString(2, userId);
            
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Password changed successfully!");
            }
        }
    }
    
    private static void showPatents() {
        System.out.println("\n=== PATENT RIGHTS ===");
        System.out.println("This software is developed and owned by:");
        System.out.println("Sumanth Railway Management Solutions Pvt. Ltd.");
        System.out.println("All rights reserved © 2023");
    }
    
    private static void logout() {
        System.out.println("\nLogging out...");
        System.out.println("Thank you for using " + APP_NAME);
    }
    
    private static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            logger.info("Connection pool shut down");
        }
        scanner.close();
    }
    
    // Utility methods for input handling
    private static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
    
    private static String getValidInput(String prompt, java.util.function.Predicate<String> validator, 
                                      String errorMessage) {
        while (true) {
            String input = getInput(prompt);
            if (validator.test(input)) {
                return input;
            }
            System.out.println(errorMessage);
        }
    }
    
    private static int getValidIntegerInput(String prompt, int min, int max) {
        while (true) {
            try {
                System.out.print(prompt);
                int value = Integer.parseInt(scanner.nextLine());
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Please enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format");
            }
        }
    }
    
    private static boolean getYesNoInput(String prompt) {
        while (true) {
            String input = getInput(prompt).toUpperCase();
            if (input.equals("Y") || input.equals("YES")) return true;
            if (input.equals("N") || input.equals("NO")) return false;
            System.out.println("Please enter Y or N");
        }
    }
    
    private static LocalDate getValidDateInput(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return LocalDate.parse(scanner.nextLine(), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD");
            }
        }
    }
    
    private static void generateTicket(String userId, int trainNo, LocalDate departureDate) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT u.full_name, t.train_name, r.berth_type, r.meals_required, r.departure_date " +
                 "FROM reservations r " +
                 "JOIN users u ON r.user_id = u.user_id " +
                 "JOIN train_details t ON r.train_no = t.train_no " +
                 "WHERE r.user_id = ? AND r.train_no = ? AND r.departure_date = ? " +
                 "ORDER BY r.booking_date DESC LIMIT 1")) {
                 
            pstmt.setString(1, userId);
            pstmt.setInt(2, trainNo);
            pstmt.setDate(3, java.sql.Date.valueOf(departureDate));
            
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                System.out.println("\n=== YOUR TICKET ===");
                System.out.println("+---------------------+---------------------+");
                System.out.println("| Passenger Name      | " + padRight(rs.getString("full_name"), 20) + " |");
                System.out.println("| Train Name          | " + padRight(rs.getString("train_name"), 20) + " |");
                System.out.println("| Berth Type          | " + padRight(rs.getString("berth_type"), 20) + " |");
                System.out.println("| Meals Included      | " + padRight(rs.getBoolean("meals_required") ? "Yes" : "No", 20) + " |");
                System.out.println("| Departure Date      | " + padRight(rs.getDate("departure_date").toString(), 20) + " |");
                System.out.println("+---------------------+---------------------+");
                System.out.println("Note: Please carry valid ID proof during journey");
            }
        }
    }
    
    private static String padRight(String s, int length) {
        return String.format("%-" + length + "s", s);
    }
    
    private static void saveUserToCSV(String userId, String username, String fullName, int age, 
                                    String phone, String aadhaar, String address, String pincode) {
        try (FileWriter fw = new FileWriter("users_backup.csv", true);
             PrintWriter pw = new PrintWriter(fw)) {
             
            if (new File("users_backup.csv").length() == 0) {
                pw.println("user_id,username,full_name,age,phone,aadhaar,address,pincode");
            }
            pw.printf("%s,%s,%s,%d,%s,%s,%s,%s%n",
                userId, username, fullName, age, phone, aadhaar, address, pincode);
                
        } catch (IOException e) {
            logger.error("Failed to save user to CSV: {}", e.getMessage());
        }
    }
}