# 🚗 ParkWise — Smart Parking Management System

**A Full-Stack Academic & Industry Project**

ParkWise is a smart parking management system designed to simplify vehicle entry and exit, automate parking slot allocation, calculate parking fees, and generate digital parking passes. It combines a Java backend, a Node.js API gateway, a responsive web interface, and a MySQL relational database.

The system follows a decoupled, multi-tier architecture that separates frontend presentation, API communication, business logic, and database persistence.

---

## ✨ Features

- 🚘 **Automated Vehicle Check-In & Check-Out** — Manage parking entry and exit records.
- 🅿️ **Dynamic Slot Allocation** — Allocate compatible parking slots for cars, bikes, and electric vehicles.
- 🟢 **Real-Time Slot Visualization** — View parking slot availability using color-coded indicators.
- 💰 **Automated Billing** — Calculate parking charges based on parking duration and applicable rates.
- 🧾 **Digital Receipts** — Generate parking billing receipts.
- 📱 **QR Code Parking Passes** — Generate digital parking passes using a Java-based QR code engine.
- ⚡ **EV Parking Support** — Support electric vehicle parking and charging-related tracking.
- 🔐 **Role-Based Login** — Provide separate access for administrators and parking operators.
- 📊 **Administrative Dashboard** — Monitor parking slots, tickets, and billing information.
- 📱 **Responsive Web Interface** — Access the application through a modern browser.
- 🛡️ **SQL Injection Protection** — Use parameterized JDBC queries with `PreparedStatement`.

## 🛠️ Technology Stack

| Layer | Technologies |
|---|---|
| Frontend | HTML5, CSS3, JavaScript ES6+ |
| API Gateway | Node.js, Express.js |
| Backend | Java, JDBC, HTTP JSON API |
| Database | MySQL |
| Database Driver | MySQL Connector/J |
| Development Tools | Visual Studio Code, MySQL Client, Web Browser |

## 🏗️ System Architecture

```text
                ┌─────────────────────────┐
                │       Web Browser       │
                │     HTML, CSS, JS       │
                └────────────┬────────────┘
                             │
                       HTTP :3000
                             │
                ┌────────────▼────────────┐
                │   Node.js / Express     │
                │      API Gateway        │
                └────────────┬────────────┘
                             │
                       HTTP JSON :8081
                             │
                ┌────────────▼────────────┐
                │    Java Core API        │
                │ Business Logic & JDBC   │
                └────────────┬────────────┘
                             │
                       JDBC Connection
                             │
                ┌────────────▼────────────┐
                │      MySQL Database     │
                │       parking_db        │
                └─────────────────────────┘
```

### Request Workflow

1. The user accesses the web application at `http://localhost:3000`.
2. The frontend sends HTTP requests to the Node.js gateway.
3. Express forwards applicable API requests to the Java backend on port `8081`.
4. The Java backend validates inputs and executes parking-related business logic.
5. JDBC communicates with MySQL to retrieve or update records.
6. The response travels back through the Java API and Node.js gateway to the frontend.

## 📂 Project Structure

The following is the expected high-level structure. Adapt file names to match your actual repository.

```text
ParkWise/
│
├── backend-java/
│   ├── ApiServer.java
│   ├── DBConnection.java
│   ├── ParkingService.java
│   ├── ReservationService.java
│   ├── Bill.java
│   ├── EvService.java
│   ├── QrCode.java
│   ├── SlotFullException.java
│   ├── Vehicle.java
│   ├── Car.java
│   ├── Bike.java
│   ├── EVCar.java
│   └── mysql-connector-j-26.7.0.jar
│
├── server-node/
│   ├── server.js
│   ├── package.json
│   └── package-lock.json
│
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
│
├── schema.sql
└── README.md
```

> **Note:** The structure above is illustrative. Keep the actual folder and file names consistent with your project. If Express serves the frontend directly from `server-node`, document that arrangement instead of creating a separate `frontend` folder.

## 🗄️ Database Design

The application uses MySQL to store persistent parking information.

| Table | Purpose |
|---|---|
| `slots` | Stores slot IDs, vehicle compatibility, and occupancy status. |
| `tickets` | Stores parking tickets, vehicle numbers, slot assignments, and entry timestamps. |
| `bills` | Stores exit timestamps, parking duration, payment methods, and collected fees. |
| `users` | Stores administrator and parking operator authentication records. |

### Database Setup

1. Start your local MySQL server.
2. Open your MySQL client or terminal.
3. Execute the following commands from the directory containing `schema.sql`:

```sql
CREATE DATABASE IF NOT EXISTS parking_db;
USE parking_db;
SOURCE schema.sql;
```

Ensure that `schema.sql` exists at the specified location and contains the required table definitions.

## ⚙️ Prerequisites

Install the following before running ParkWise:

- Java JDK 8 or later, compatible with the source code.
- Node.js and npm.
- MySQL Server.
- Visual Studio Code or another code editor.
- A modern web browser.
- MySQL Connector/J JAR compatible with your MySQL server and Java environment.

Verify your installations:

```bash
java -version
javac -version
node -v
npm -v
```

## 🚀 Installation & Execution

### Step 1: Clone the Repository

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd ParkWise
```

Replace the placeholder with your actual GitHub repository URL.

### Step 2: Configure MySQL

Create the database and initialize the tables using the SQL commands in the database setup section.

Open `backend-java/DBConnection.java` or your corresponding configuration file and verify the database URL, username, and password.

Example configuration:

```text
Database: parking_db
Host: localhost
Port: 3306
```

Use your own local credentials. Do not commit database passwords to GitHub.

### Step 3: Start the Java Backend

Open the first VS Code terminal:

```bash
cd backend-java
```

Compile the Java source files:

```powershell
javac -cp ".;mysql-connector-j-26.7.0.jar" *.java
```

Start the API server:

```powershell
java -cp ".;mysql-connector-j-26.7.0.jar" ApiServer
```

The expected output, if startup and database connection succeed, is similar to:

```text
JSON API started at http://localhost:8081
Connected to MySQL database successfully.
```

Keep this terminal running.

> **Windows note:** The semicolon (`;`) separates classpath entries on Windows. On Linux or macOS, use a colon (`:`) instead.

### Step 4: Start the Node.js Gateway

Open a second terminal in VS Code:

```bash
cd server-node
npm install
npm start
```

The `package.json` file must define the appropriate `start` script.

Expected output, if startup succeeds:

```text
Parking gateway listening on http://localhost:3000
Proxying Java API requests to http://localhost:8081
```

Keep this terminal running as well.

### Step 5: Open the Application

Open your browser and visit:

**http://localhost:3000**

If the application has been configured with the sample credentials below, use them for local testing:

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `admin123` |
| Parking Operator | `guard` | `guard123` |

These are example credentials from the project configuration and will work only if they are actually configured in your database or authentication logic.

**Security warning:** Change default passwords before any real deployment. Never use demonstration credentials in production.

## 🎨 Parking Slot Indicators

The interface uses color-coded indicators to make parking availability easier to understand.

| Color | Meaning |
|---|---|
| 🟢 Green | Available parking slot |
| 🔴 Red | Occupied parking slot |
| 🔵 Blue | EV charging station |

Actual behavior depends on the frontend rendering logic and slot status returned by the backend.

## 👥 Team Member Contributions

The project is divided into three primary areas of responsibility.

### Member 1 — Database Architect & Core Java Engineer

- Designed the relational database schema, constraints, indexes, and foreign keys.
- Implemented JDBC connectivity and database initialization.
- Developed vehicle models such as `Vehicle`, `Car`, `Bike`, and `EVCar`.
- Implemented custom exceptions and database transaction handling.

**Key learning areas:** Database normalization, ACID properties, JDBC, SQL, and OOP polymorphism.

### Member 2 — Backend Services & API Gateway Engineer

- Developed parking allocation, reservation, and billing services.
- Implemented the Java HTTP JSON API.
- Configured the Express API gateway and backend request forwarding.
- Handled request validation, response formatting, and error handling.

**Key learning areas:** REST APIs, HTTP status codes, reverse proxies, concurrency, and backend architecture.

### Member 3 — Frontend Developer & UI/UX Specialist

- Designed the dashboard, login interface, and parking slot visualization.
- Implemented frontend API communication using JavaScript `fetch` and `async/await`.
- Developed modal dialogs, notifications, receipts, and QR pass displays.
- Designed responsive layouts and client-side form validation.

**Key learning areas:** DOM manipulation, asynchronous JavaScript, responsive CSS, and XSS prevention.

## 🔒 Security & Reliability

ParkWise incorporates or is designed to support the following practices:

- **Parameterized SQL queries:** Use JDBC `PreparedStatement` for user-supplied values.
- **Input validation:** Validate vehicle numbers, slot assignments, and transaction details.
- **Error handling:** Handle invalid requests, unavailable slots, and database failures.
- **Role-based access:** Separate administrator and operator permissions.
- **Transaction consistency:** Use database transactions where multiple related updates must succeed together.
- **Frontend output safety:** Escape or safely render user-controlled content to reduce XSS risks.
- **Credential protection:** Keep database credentials and production secrets outside version control.

Security features should be verified against the actual implementation before claiming that they are fully enforced.

## 🧰 Troubleshooting

| Issue | Possible Cause | Solution |
|---|---|---|
| `ERR_CONNECTION_REFUSED` | Java API or Node.js server is stopped. | Start both servers and verify ports `8081` and `3000`. |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | JDBC driver is missing from the classpath. | Check the JAR filename and classpath. |
| `Access denied for user 'root'@'localhost'` | Incorrect database credentials or insufficient privileges. | Verify the credentials in the database configuration. |
| `EADDRINUSE` | Another process is using the required port. | Stop the conflicting process or configure a different port. |
| `npm start` fails | Missing dependencies or incorrect start script. | Run `npm install` and inspect `package.json`. |
| Tables not found | The schema has not been initialized or the wrong database is selected. | Run `schema.sql` and verify `parking_db`. |
| API returns an error | Invalid request, backend exception, or database issue. | Inspect both server terminals and check the request payload. |

### Finding a Port Conflict on Windows

Check the process using port `3000`:

```powershell
netstat -ano | findstr :3000
```

Identify the PID and terminate the process only if you have confirmed it is safe to stop:

```powershell
taskkill /PID <PID> /F
```

Replace `<PID>` with the actual process ID. Repeat for port `8081` if necessary.

## 🎓 Academic & Industry Relevance

ParkWise demonstrates practical implementation of:

- Full-stack web application development.
- Multi-tier software architecture.
- Object-oriented programming in Java.
- Database design and relational data management.
- JDBC integration and SQL transactions.
- API gateway and HTTP communication.
- Dynamic frontend rendering.
- Parking resource allocation and billing automation.

The project can be extended with online payments, advance reservations, live sensor integration, license plate recognition, analytics, and cloud deployment.

## 🔮 Future Enhancements

- Real-time IoT sensor integration for slot detection.
- Online payment gateway integration.
- Advance parking reservations.
- Automated number plate recognition.
- EV charging session and energy billing.
- Email or SMS notifications.
- Analytics for parking utilization and revenue.
- Cloud hosting and centralized monitoring.

## 👨‍💻 Project Information

**Project Name:** ParkWise — Smart Parking Management System  
**Project Type:** Full-Stack Academic & Industry Project  
**Backend:** Java  
**API Gateway:** Node.js and Express.js  
**Frontend:** HTML, CSS, JavaScript  
**Database:** MySQL  
**Architecture:** Multi-Tier Architecture

---

## 📄 License

This project is intended for academic and educational purposes. Add a suitable open-source license, such as the MIT License, only if you intend to distribute the project under those terms.

---

⭐ If you find this project useful, consider starring the repository and sharing your feedback.

**ParkWise — Smarter Parking, Better Management.**
