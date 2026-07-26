<div align="center">
  <img src="https://via.placeholder.com/120x120.png?text=LAN+Msg" alt="LanMessenger Logo" width="120" height="120" style="border-radius:20px;"/>

  # 💬 LanMessenger
  **A Secure, Fast, and Modern Local Area Network Chat Application**

  [![Java Version](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.java.net/)
  [![Maven](https://img.shields.io/badge/Maven-3.8-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
  [![FlatLaf](https://img.shields.io/badge/UI-FlatLaf-0078D7?style=for-the-badge)](https://www.formdev.com/flatlaf/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

  [Features](#-features) • [Installation](#-installation) • [Usage](#-usage) • [Architecture](#-architecture) • [Contributing](#-contributing)
</div>

---

## 📖 Overview

**LanMessenger** is a lightweight, cross-platform desktop messaging application designed specifically for **Local Area Networks (LAN)**. Built with Java and utilizing the sleek **FlatLaf** Look and Feel, it provides a seamless and secure environment for team communication, file sharing, and private discussions without requiring an active internet connection.

Ideal for offices, schools, and private local networks where privacy, speed, and offline availability are paramount.

## ✨ Features

- 🔒 **End-to-End Security** – Built-in message encryption using custom `CryptoUtil` ensures that local network sniffers cannot intercept your conversations.
- ⚡ **Real-Time Communication** – Socket-based Client-Server architecture for zero-latency messaging.
- 🎨 **Modern User Interface** – Powered by [FlatLaf](https://www.formdev.com/flatlaf/), offering a clean, native-looking interface that escapes the traditional Java Swing look.
- 👥 **Global & Private Rooms** – Chat with everyone in the network or start a secure 1-on-1 private session.
- 📎 **File Transfer** – Seamlessly send and receive documents and media files within your chat.
- ↩️ **Reply System** – Contextual replies to specific messages, keeping conversations organized.

## 🚀 Installation

### Prerequisites
Before you begin, ensure you have met the following requirements:
* **Java Development Kit (JDK)**: Version 17 or higher.
* **Apache Maven**: For dependency management and building the project.

### Clone the Repository
```bash
git clone https://github.com/fitorifqi/LanMessenger.git
cd LanMessenger
```

### Build with Maven
Download dependencies and compile the project using the following command:
```bash
mvn clean install
```

## 💻 Usage

LanMessenger requires one **Server** instance to route messages, and multiple **Client** instances to connect to it.

### 1. Start the Server
Run the server to start listening for incoming client connections:
```bash
mvn exec:java -Dexec.mainClass="projek.lanmessanger.ChatServer"
```
> **Note:** The server runs on port `5000` by default. Note the IP address of the machine running the server.

### 2. Start the Client
Open a new terminal window and run the client:
```bash
mvn exec:java -Dexec.mainClass="projek.lanmessanger.ChatClient"
```
Upon launching, a configuration window will appear. 
- Enter the **IP Address** of the Server (use `localhost` or `127.0.0.1` if testing on the same machine).
- Enter the **Port** (`5000`).
- Click **OK** to connect and start messaging!

## 📸 Screenshots

<div align="center">

| Port Configuration | Login Screen | Global Chat Lobby |
| :---: | :---: | :---: |
| <img src="image/milihport.png" width="250" alt="Port Configuration"/> | <img src="image/login.png" width="250" alt="Login Screen"/> | <img src="image/lobby.png" width="250" alt="Global Chat Lobby"/> |
</div>

## 🏗️ Architecture

```text
LanMessenger/
├── pom.xml                        # Maven configuration & dependencies
└── src/main/java/projek/lanmessanger/
    ├── ChatServer.java            # Central server logic, client handler & dispatcher
    ├── ChatClient.java            # Main GUI client application (Global Chat)
    ├── NetworkClient.java         # TCP/IP socket connection handler for the client
    ├── PrivateChatWindow.java     # Dedicated GUI for 1-on-1 private messaging
    ├── CryptoUtil.java            # Cryptography utility for encrypting/decrypting messages
    └── ClientListener.java        # Interface handling incoming network events
```

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---
<div align="center">
  Made with ❤️ by Fito Rifqi
</div>
