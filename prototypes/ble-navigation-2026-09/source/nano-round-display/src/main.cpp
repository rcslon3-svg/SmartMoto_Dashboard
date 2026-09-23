#include <Arduino.h>
#include <Adafruit_GC9A01A.h>
#include <Fonts/FreeSansBold18pt7b.h>
#include <Fonts/FreeSans9pt7b.h>

// Classic 5 V Nano: ALL five signals need 5 V -> 3.3 V translation.
// Hardware SPI: MOSI D11, SCK D13. D4-D7 are reserved for the motor.
Adafruit_GC9A01A display(10, 9, 8); // CS, DC, RST
// Small monochrome strip + one RGB565 scanline, never a full framebuffer.
GFXcanvas1 strip(240, 8);
uint16_t pixels[240];
constexpr uint32_t kPageIntervalMs = 1000;
constexpr uint32_t kSpiHz = 4000000;
constexpr uint16_t kBackground = 0x0000;
uint32_t lastPageMs;
bool navigation = false;

// Demo values only. No distance sensor, phone connection or EEPROM writes.
const char kOdometer[] = "000000";
const char kTrip[] = "888.0";
const char kDistance[] = "500 m";
const char kStreet[] = "Main St.";

void centered(const char *text, const GFXfont *font, int16_t baseline,
              int16_t stripY) {
    strip.setFont(font);
    int16_t x1, y1;
    uint16_t width, height;
    strip.getTextBounds(text, 0, 0, &x1, &y1, &width, &height);
    strip.setCursor((240 - static_cast<int16_t>(width)) / 2 - x1,
                    baseline - stripY);
    strip.print(text);
}

void drawScene(int16_t y) {
    strip.fillScreen(0);
    strip.drawCircle(119, 119 - y, 116, 1);
    strip.drawCircle(119, 119 - y, 112, 1);
    if (!navigation) {
        centered("ODOMETER", &FreeSans9pt7b, 55, y);
        centered(kOdometer, &FreeSansBold18pt7b, 99, y);
        centered("km", &FreeSans9pt7b, 122, y);
        strip.drawFastHLine(45, 135 - y, 150, 1);
        centered("TRIP", &FreeSans9pt7b, 158, y);
        centered(kTrip, &FreeSansBold18pt7b, 193, y);
        centered("km", &FreeSans9pt7b, 213, y);
    } else {
        // Rounded right-turn arrow, drawn entirely with vector primitives.
        strip.fillRoundRect(84, 61 - y, 65, 45, 17, 1);
        strip.fillRect(104, 83 - y, 46, 28, 0);
        strip.fillRect(84, 82 - y, 20, 39, 1);
        strip.fillTriangle(139, 44 - y, 139, 100 - y, 171, 72 - y, 1);
        centered(kDistance, &FreeSansBold18pt7b, 165, y);
        strip.drawFastHLine(45, 180 - y, 150, 1);
        centered(kStreet, &FreeSans9pt7b, 204, y);
    }
}

void renderPage() {
    const uint32_t started = millis();
    for (int16_t y = 0; y < 240; y += 8) {
        drawScene(y);
        display.startWrite();
        display.setAddrWindow(0, y, 240, 8);
        for (uint8_t row = 0; row < 8; ++row) {
            // Ice-blue accents, warm white digits on the odometer screen.
            const int16_t screenY = y + row;
            const uint16_t ink = navigation ? 0xD75F :
                ((screenY >= 65 && screenY <= 122) ||
                 (screenY >= 165 && screenY <= 213) ? 0xFFBC : 0x8D5B);
            const uint8_t *bits = strip.getBuffer() + row * 30;
            for (uint16_t x = 0; x < 240; ++x) {
                pixels[x] = bits[x / 8] & (0x80 >> (x & 7)) ? ink : kBackground;
            }
            display.writePixels(pixels, 240);
        }
        display.endWrite();
    }
    Serial.print(navigation ? F("NAV render_ms=") : F("ODO render_ms="));
    Serial.println(millis() - started);
}

void setup() {
    Serial.begin(115200);
    if (!strip.getBuffer()) {
        Serial.println(F("ERROR: strip allocation failed"));
        while (true) {}
    }
    strip.setTextColor(1);
    strip.setTextWrap(false);
    display.begin(kSpiHz);
    display.setRotation(0); // Change to 1/2/3 to match physical mounting.
    display.fillScreen(kBackground);
    lastPageMs = millis();
    renderPage();
}

void loop() {
    const uint32_t now = millis();
    if (static_cast<uint32_t>(now - lastPageMs) < kPageIntervalMs) return;
    lastPageMs += kPageIntervalMs;
    if (static_cast<uint32_t>(now - lastPageMs) >= kPageIntervalMs) lastPageMs = now;
    navigation = !navigation;
    renderPage();
}
