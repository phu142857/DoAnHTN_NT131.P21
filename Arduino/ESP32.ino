// ---------- Thư viện ----------
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <DHT.h>
#include <MQ135.h>
#include <NTPClient.h>
#include <WiFiUdp.h>
#include <TimeLib.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// ---------- WiFi ----------
#define WIFI_SSID "Do-An-HTN"
#define WIFI_PASSWORD "123456789"

// ---------- Firebase ----------
#define DATABASE_URL "YOUR_DATABASE_URL"
#define API_KEY "YOUR_API_KEY"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ---------- NTP ----------
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pool.ntp.org", 7 * 3600, 60000);

// ---------- DHT22 ----------
#define DHTPIN 4
#define DHTTYPE DHT22
DHT dht(DHTPIN, DHTTYPE);

// ---------- MQ135 ----------
#define MQ135_PIN 34
MQ135 mq135_sensor(MQ135_PIN);

// ---------- GP2Y1010AU0F ----------
#define GP2Y_LED_PIN 35
#define GP2Y_ANALOG_PIN 32
float dustDensity = 0.0;

// ---------- OLED ----------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define SCREEN_ADDRESS 0x3C
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ---------- Quạt ----------
#define FAN_PIN 2

// ---------- Cảnh báo ----------
float ppmDanger = 100;
float tempDanger = 30.0;

// ---------- Biến toàn cục ----------
float temperature = 0.0, humidity = 0.0, ppm = 0.0;
bool fanState = false;
unsigned long lastSendTime = 0;
const unsigned long interval = 30 * 60 * 1000; // 30 phút

// ---------- Hàm đo bụi (có giá trị giả nếu lỗi) ----------
float readDustGP2Y() {
  digitalWrite(GP2Y_LED_PIN, LOW);
  delayMicroseconds(280);
  int raw = analogRead(GP2Y_ANALOG_PIN);
  delayMicroseconds(40);
  digitalWrite(GP2Y_LED_PIN, HIGH);
  delayMicroseconds(9680);

  float voltage = raw * (5.0 / 4095.0);
  float dustDensity = (voltage - 0.9) * 1000.0 / 0.5;

  return dustDensity;
}

// ---------- Task1: Đọc cảm biến + OLED ----------
void taskReadSensor(void *pvParameters) {
  for (;;) {
    ppm = mq135_sensor.getPPM();
    temperature = dht.readTemperature();
    humidity = dht.readHumidity();
    dustDensity = readDustGP2Y();

    if (isnan(temperature) || isnan(humidity)) {
      Serial.println("❌ [Sensor] Lỗi đọc cảm biến");
    } else {
      Serial.printf("📡 [Sensor] PPM: %.2f | Temp: %.2f°C | Humi: %.2f%% | Dust: %.2f ug/m3\n", ppm, temperature, humidity, dustDensity);

      // Hiển thị OLED
      display.clearDisplay();
      display.setTextSize(1);
      display.setTextColor(SSD1306_WHITE);
      display.setCursor(0, 0);
      display.printf("PPM: %.2f", ppm);
      display.setCursor(0, 16);
      display.printf("Temp: %.2f C", temperature);
      display.setCursor(0, 32);
      display.printf("Humi: %.2f %%", humidity);
      display.setCursor(0, 48);
      display.printf("Dust: %.2f ug/m3", dustDensity);
      display.display();
    }
    vTaskDelay(10000 / portTICK_PERIOD_MS);
  }
}

// ---------- Task2: Điều khiển quạt ----------
void taskControlFan(void *pvParameters) {
  for (;;) {
    bool shouldTurnOnFan = (ppm > ppmDanger || temperature > tempDanger || dustDensity > 80);
    if (shouldTurnOnFan && !fanState) {
      digitalWrite(FAN_PIN, HIGH);
      fanState = true;
      Serial.println("🌀 [Fan] BẬT QUẠT");
    } else if (!shouldTurnOnFan && fanState) {
      digitalWrite(FAN_PIN, LOW);
      fanState = false;
      Serial.println("✅ [Fan] TẮT QUẠT");
    }
    vTaskDelay(5000 / portTICK_PERIOD_MS);
  }
}

// ---------- Task3: Gửi Firebase ----------
void taskSendFirebase(void *pvParameters) {
  for (;;) {
    timeClient.update();
    unsigned long epochTime = timeClient.getEpochTime();
    String currentDate = String(day(epochTime)) + "-" + String(month(epochTime)) + "-" + String(year(epochTime));
    String currentTime = String(hour(epochTime)) + ":" + String(minute(epochTime));

    bool sendNow = false;
    if (ppm > ppmDanger || temperature > tempDanger || dustDensity > 80 || millis() - lastSendTime >= interval) {
      sendNow = true;
    }

    if (sendNow) {
      Serial.println("🚩 [Firebase] Đang gửi dữ liệu...");
      bool success = true;
      success &= Firebase.RTDB.setFloat(&fbdo, "/air_quality/ppm", ppm);
      success &= Firebase.RTDB.setFloat(&fbdo, "/air_quality/temperature", temperature);
      success &= Firebase.RTDB.setFloat(&fbdo, "/air_quality/humidity", humidity);
      success &= Firebase.RTDB.setFloat(&fbdo, "/air_quality/dust_density", dustDensity);
      success &= Firebase.RTDB.setBool(&fbdo, "/air_quality/fan_status", fanState);
      success &= Firebase.RTDB.setString(&fbdo, "/air_quality/date", currentDate);
      success &= Firebase.RTDB.setString(&fbdo, "/air_quality/time", currentTime);

      if (success) {
        Serial.println("✅ [Firebase] Gửi thành công");
        lastSendTime = millis();
      } else {
        Serial.println("❌ [Firebase] Lỗi: " + fbdo.errorReason());
      }
    }
    vTaskDelay(10000 / portTICK_PERIOD_MS);
  }
}

// ---------- Setup ----------
void setup() {
  Serial.begin(115200);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("⏳ Đang kết nối WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(1000);
  }
  Serial.println("\n✅ WiFi đã kết nối");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  dht.begin();
  timeClient.begin();
  randomSeed(analogRead(0)); // Khởi tạo số ngẫu nhiên

  pinMode(FAN_PIN, OUTPUT);
  digitalWrite(FAN_PIN, LOW);
  pinMode(GP2Y_LED_PIN, OUTPUT);
  digitalWrite(GP2Y_LED_PIN, HIGH);

  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("❌ OLED không khởi động được"));
    while (1);
  }
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Khoi dong OLED OK");
  display.display();
  delay(2000);

  xTaskCreatePinnedToCore(taskReadSensor, "ReadSensor", 5120, NULL, 2, NULL, 1);
  xTaskCreatePinnedToCore(taskControlFan, "ControlFan", 2048, NULL, 1, NULL, 1);
  xTaskCreatePinnedToCore(taskSendFirebase, "SendFirebase", 12288, NULL, 1, NULL, 1);
}

// ---------- Loop ----------
void loop() {
  vTaskDelay(1000 / portTICK_PERIOD_MS);
}
