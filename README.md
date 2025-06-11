Mini Project: Air Quality Measurement Using ESP32 & Kotlin
Project Description
This project utilizes an ESP32 microcontroller and a Kotlin-based Android application to measure air quality.Data from the air quality sensor is transmitted to a Firebase Realtime Database and displayed in real-time on a mobile app.

Features
- Real-Time Monitoring: Measure and monitor air quality data in real-time.
- Firebase Integration: Use Firebase Realtime Database for data storage and synchronization.
- User-Friendly App: Kotlin-based Android application for seamless interaction and visualization of data.

Setup Instructions
1. ESP32 Configuration
- Open the ESP32.ino file in Arduino IDE.
- Update your Wi-Fi credentials in the code:
    const char* ssid = "Your_SSID";
    const char* password = "Your_PASSWORD";
- Ensure the Firebase URL and credentials match the following:
    #define FIREBASE_HOST "Your_Database_URL "
    #define FIREBASE_AUTH "Your_Database_Authencation_Code"
- Upload the code to the ESP32 using a USB connection.

2. Android Application Configuration
- Open the Android project in Android Studio.
- Place the provided google-services.json file in the Android/app/ directory.
- Ensure the package_name matches your app's configuration (com.example.appcontrol).

How to Run
- Power on the ESP32 and verify it connects to your Wi-Fi network.
- Launch the Android application on your device.
- View real-time air quality data retrieved from the Firebase database.

System Overview
ESP32 Code
- File: ESP32.ino
- Functionality:
    • Reads air quality data from the connected sensor.
  
    • Sends data to Firebase Realtime Database using HTTP.

Android Application
- Package Name: com.example.appcontrol
- Displays real-time air quality data using Firebase integration.

Expected Outcome
- A fully operational system measuring air quality and displaying data in the mobile app.
- Synchronization between the ESP32 and Android app via Firebase.

Contact
For more information, feel free to reach out:

Nguyen Tai Phu (Email: nguyentaiphu980@gmail.com)















