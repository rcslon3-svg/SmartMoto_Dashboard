#include <Arduino.h>
#include <SPI.h>
#include <Adafruit_GC9A01A.h>
#include <Fonts/FreeSans9pt7b.h>
#include <Fonts/FreeSansBold18pt7b.h>
#include <Fonts/FreeSansBold24pt7b.h>
#include "NavBle.h"

// Waveshare ESP32-S3-LCD-1.47B, external GC9A01 240x240.
constexpr int kSck = 4, kMosi = 5, kCs = 6, kDc = 7, kReset = 8;
constexpr int kOnboardBacklight = 46, kOnboardCs = 42;
constexpr uint32_t kSpiHz = 20000000;
constexpr uint32_t kTickIntervalMs = 1000;
constexpr uint32_t kAnimationMs = 200;
constexpr uint32_t kFrameMs = 16;
constexpr uint8_t kRotation = 0;
constexpr uint16_t kWhite = 0xEF7D, kIce = 0xBF1F, kMuted = 0x7C32;

Adafruit_GC9A01A display(&SPI, kDc, kCs, kReset);
// 115200 bytes in internal RAM. PSRAM is not needed by this demo.
GFXcanvas16 frame(240, 240);
uint32_t lastTickMs = 0;
// Integer units of 100 m avoid floating-point rounding at kilometre boundaries.
// Demo only: values reset at power-on; no flash writes each second.
constexpr uint32_t kInitialTripTenths = 8880;
constexpr uint32_t kInitialOdometerKm = 0;
uint32_t tripTenths = kInitialTripTenths;
uint32_t odometerKm = kInitialOdometerKm;
char odometerText[16];
char tripText[16];

uint16_t rgb(uint8_t r, uint8_t g, uint8_t b) {
    return ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
}

void centered(const char *text, const GFXfont *font, int baseline, uint16_t color) {
    frame.setFont(font);
    frame.setTextColor(color);
    int16_t x, y;
    uint16_t w, h;
    frame.getTextBounds(text, 0, 0, &x, &y, &w, &h);
    frame.setCursor((240 - static_cast<int>(w)) / 2 - x, baseline);
    frame.print(text);
}

void background() {
    uint16_t *buffer = frame.getBuffer();
    for (int y = 0; y < 240; ++y) {
        for (int x = 0; x < 240; ++x) {
            const int dx = x - 120, dy = y - 120;
            const int r2 = dx * dx + dy * dy;
            // Subtle blue glow towards the lower rim, almost black centre.
            const int gx = x - 120, gy = y - 218;
            const int glow = max(0, 8000 - gx * gx - gy * gy) / 160;
            buffer[y * 240 + x] = r2 > 118 * 118 ? 0 :
                rgb(2, 5 + glow / 3, 10 + glow);
        }
    }
    frame.drawCircle(119, 119, 116, rgb(49, 67, 85));
    frame.drawCircle(119, 119, 113, rgb(12, 26, 43));
}

void divider(int y) {
    for (int x = 40; x < 200; ++x) {
        const int brightness = (80 - abs(x - 120)) * 2;
        frame.drawPixel(x, y, rgb(brightness / 2, brightness / 2,
                                brightness / 2 + 12));
    }
}

void drawOdometer() {
    snprintf(odometerText, sizeof(odometerText), "%06lu",
             static_cast<unsigned long>(odometerKm));
    snprintf(tripText, sizeof(tripText), "%lu.%lu",
             static_cast<unsigned long>(tripTenths / 10),
             static_cast<unsigned long>(tripTenths % 10));
    centered("ODOMETER", &FreeSans9pt7b, 55, kMuted);

    centered("km", &FreeSans9pt7b, 127, kMuted);
    divider(140);
    centered("TRIP", &FreeSans9pt7b, 165, kIce);

    centered("km", &FreeSans9pt7b, 219, kMuted);
}

// Each digit has a clipped drum window. The frame retains only the static
// background/labels, so moving glyphs cannot overwrite labels or leave trails.
struct DrumRow {
    GFXcanvas1 before{240, 42};
    GFXcanvas1 after{240, 42};
    char previous[16] = "";
    bool dirty[16] = {};
    int slots = 6, left = 0, top, height, cell, baseline;
    const GFXfont *font;
    DrumRow(int y, int h, int width, int base, const GFXfont *f)
        : top(y), height(h), cell(width), baseline(base), font(f) {}
};
DrumRow odoDrums(68, 42, 28, 36, &FreeSansBold24pt7b);
DrumRow tripDrums(172, 32, 21, 27, &FreeSansBold18pt7b);
uint16_t tile[28 * 42];

char alignedCharacter(const char *text, int slot, int slots) {
    const int index = slot - (slots - static_cast<int>(strlen(text)));
    return index < 0 ? ' ' : text[index];
}

void prepareDrums(DrumRow &row, const char *next) {
    row.slots = max(6, static_cast<int>(strlen(next)));
    row.left = (240 - row.slots * row.cell) / 2;
    row.before.fillScreen(0);
    row.after.fillScreen(0);
    row.before.setTextWrap(false);
    row.after.setTextWrap(false);
    row.before.setFont(row.font);
    row.after.setFont(row.font);
    row.before.setTextColor(1);
    row.after.setTextColor(1);
    for (int i = 0; i < row.slots; ++i) {
        const char oldDigit = alignedCharacter(row.previous, i, row.slots);
        const char newDigit = alignedCharacter(next, i, row.slots);
        row.dirty[i] = oldDigit != newDigit;
        GFXcanvas1 *masks[] = {&row.before, &row.after};
        const char digits[] = {oldDigit, newDigit};
        for (int n = 0; n < 2; ++n) {
            char text[] = {digits[n], 0};
            int16_t x, y;
            uint16_t w, h;
            masks[n]->getTextBounds(text, 0, 0, &x, &y, &w, &h);
            masks[n]->setCursor(row.left + i * row.cell +
                               (row.cell - static_cast<int>(w)) / 2 - x,
                               row.baseline);
            masks[n]->print(text);
        }
    }
    snprintf(row.previous, sizeof(row.previous), "%s", next);
}

void paintDrums(DrumRow &row, uint32_t elapsed, bool continuous = false) {
    for (int i = 0; i < row.slots; ++i) {
        if (!row.dirty[i]) continue;
        const bool hundredMeters = continuous && &row == &tripDrums && i == row.slots - 1;
        const uint32_t duration = hundredMeters ? kTickIntervalMs : kAnimationMs;
        const int offset = row.height * min(elapsed, duration) / duration;
        const int left = row.left + i * row.cell;
        for (int y = 0; y < row.height; ++y) {
            const int oldY = y + offset;
            const int newY = y + offset - row.height;
            for (int x = 0; x < row.cell; ++x) {
                const bool ink =
                    (oldY < row.height && row.before.getPixel(left + x, oldY)) ||
                    (newY >= 0 && row.after.getPixel(left + x, newY));
                tile[y * row.cell + x] = ink ? kWhite :
                    frame.getBuffer()[(row.top + y) * 240 + left + x];
            }
        }
        display.startWrite();
        display.setAddrWindow(left, row.top, row.cell, row.height);
        display.writePixels(tile, row.cell * row.height);
        display.endWrite();
        if (!hundredMeters && elapsed >= duration) row.dirty[i] = false;
    }
}

// Predict only the fractional digit. Whole kilometres carry at the tick boundary.
void prepareContinuousTrip() {
    prepareDrums(tripDrums, tripText);
    const int slot = tripDrums.slots - 1;
    const int left = tripDrums.left + slot * tripDrums.cell;
    GFXcanvas1 *masks[] = {&tripDrums.before, &tripDrums.after};
    for (int n = 0; n < 2; ++n) {
        masks[n]->fillRect(left, 0, tripDrums.cell, 42, 0);
        char digit[] = {static_cast<char>('0' + (tripTenths + n) % 10), 0};
        int16_t x, y;
        uint16_t w, h;
        masks[n]->getTextBounds(digit, 0, 0, &x, &y, &w, &h);
        masks[n]->setCursor(left + (tripDrums.cell - static_cast<int>(w)) / 2 - x,
                            tripDrums.baseline);
        masks[n]->print(digit);
    }
    tripDrums.dirty[slot] = true;
}

uint32_t animationStartedMs = 0, lastFrameMs = 0;
bool navShowing = false;
uint32_t drawnNavRevision = 0;

void drawNavigation(const NavData &nav) {
    background();
    centered("NEXT", &FreeSans9pt7b, 27, kIce);
    if (nav.hasIcon) {
        for (int y = 0; y < 48; ++y) for (int x = 0; x < 48; ++x) {
            if (nav.icon[y * 6 + x / 8] & (0x80 >> (x % 8)))
                frame.fillRect(72 + x * 2, 33 + y * 2, 2, 2, kWhite);
        }
    } else centered("MAPS", &FreeSansBold18pt7b, 103, kWhite);
    centered(nav.turn, &FreeSansBold24pt7b, 176, kWhite);
    centered("TO GO", &FreeSans9pt7b, 199, kMuted);
    centered(nav.remaining, &FreeSansBold18pt7b, 226, kIce);
    display.startWrite();
    display.setAddrWindow(0, 0, 240, 240);
    display.writePixels(frame.getBuffer(), 240UL * 240UL);
    display.endWrite();
    drawnNavRevision = nav.revision;
    Serial.printf("nav_screen turn=%s remaining=%s icon=%u\n",
                  nav.turn, nav.remaining, nav.hasIcon);
}

void initializeScreen() {
    background();
    drawOdometer(); // Formats values and draws only static labels.
    display.startWrite();
    display.setAddrWindow(0, 0, 240, 240);
    display.writePixels(frame.getBuffer(), 240UL * 240UL);
    display.endWrite();
    prepareDrums(odoDrums, odometerText);
    prepareDrums(tripDrums, tripText);
    paintDrums(odoDrums, kAnimationMs);
    paintDrums(tripDrums, kAnimationMs);
}

void startAnimation() {
    drawOdometer();
    prepareDrums(odoDrums, odometerText);
    prepareContinuousTrip();
    animationStartedMs = lastTickMs;
    lastFrameMs = animationStartedMs;

}

void updateAnimation() {
    const uint32_t now = millis();
    if (now - lastFrameMs < kFrameMs) return;
    lastFrameMs = now;
    const uint32_t elapsed = now - animationStartedMs;
    paintDrums(odoDrums, elapsed);
    paintDrums(tripDrums, elapsed, true);
}

void setup() {
    Serial.begin(115200); // Never wait for USB monitor: standalone boot works too.
    digitalWrite(kOnboardBacklight, LOW);
    pinMode(kOnboardBacklight, OUTPUT);
    digitalWrite(kOnboardCs, HIGH);
    pinMode(kOnboardCs, OUTPUT);
    if (!frame.getBuffer() || !odoDrums.before.getBuffer() ||
        !odoDrums.after.getBuffer() || !tripDrums.before.getBuffer() ||
        !tripDrums.after.getBuffer()) {
        while (true) {
            Serial.println("ERROR: framebuffer allocation failed");
            delay(1000);
        }
    }
    frame.setTextWrap(false);
    SPI.begin(kSck, -1, kMosi, kCs);
    display.begin(kSpiHz);
    display.setRotation(kRotation);
    initializeScreen();
    lastTickMs = millis();
    startAnimation();
    navBleBegin();
}

void loop() {
    const uint32_t now = millis();
    const NavData nav = navBleSnapshot();
    const bool wantNav = nav.valid && ((now / 1000) % 2 == 1);
    if (wantNav) {
        if (!navShowing || nav.revision != drawnNavRevision) drawNavigation(nav);
        navShowing = true;
    } else if (navShowing) {
        navShowing = false;
        odoDrums.previous[0] = 0;
        tripDrums.previous[0] = 0;
        initializeScreen();
        startAnimation();
    }
    if (static_cast<uint32_t>(now - lastTickMs) >= kTickIntervalMs) {
        // Finish the previous drum turn before preparing the next pair.
        if (!navShowing) {
            paintDrums(odoDrums, kTickIntervalMs);
            paintDrums(tripDrums, kTickIntervalMs, true);
        }
        const uint32_t ticks = static_cast<uint32_t>(now - lastTickMs) / kTickIntervalMs;
        lastTickMs += ticks * kTickIntervalMs;
        odometerKm += (tripTenths % 10 + ticks) / 10;
        tripTenths += ticks;
        if (!navShowing) startAnimation();
        Serial.printf("page=ODO odo=%s trip=%s drum_period_ms=%lu tick_ms=%lu heap=%u\n",
                      odometerText, tripText, static_cast<unsigned long>(kTickIntervalMs),
                      static_cast<unsigned long>(lastTickMs), ESP.getFreeHeap());
    }
    if (!navShowing) updateAnimation();
    delay(1);
}
