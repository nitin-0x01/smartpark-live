PARKING MANAGEMENT SYSTEM v2 (localhost:8080)
==============================================
Features: Admin/Guard login, live slot map, automatic billing, QR tickets,
UPI payments, online reservations, revenue analytics, and EV charging.

FIRST-TIME SETUP
 1. Run schema.sql in MySQL. Do not run this against an existing database; it drops tables.
 2. Run upgrade.sql in MySQL. It safely adds the newer tables and can be run again.
 3. Set the MySQL password in DBConnection.java.
 4. Set UPI_VPA and UPI_NAME in Config.java.
 5. Compile: javac -cp ".;mysql-connector-j-x.x.x.jar" *.java (Mac/Linux: use : instead of ;)
 6. Run:     java -cp ".;mysql-connector-j-x.x.x.jar" WebServer
 7. Open:    http://localhost:8080

EXISTING v1 DATABASE
 - Run only upgrade.sql; existing data is preserved.
 - Restore the existing database password in DBConnection.java if required.
 - To return to v1, run rollback_upgrade.sql.

LOGIN
 admin / admin123 (full access) | guard / guard123 (entry, exit, EV, and reservation check-in)

PUBLIC PAGES (no login required)
 /map  /reserve  /reservation  /verify

PHONE QR SCANNING
Set BASE_URL in Config.java to http://<PC-IP>:8080 so the phone and computer
can access the server on the same Wi-Fi network.
