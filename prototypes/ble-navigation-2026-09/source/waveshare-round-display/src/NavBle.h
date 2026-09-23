#pragma once

#include <Arduino.h>

struct NavData {
    bool valid = false;
    bool hasIcon = false;
    char turn[9] = {};
    char remaining[9] = {};
    uint8_t icon[288] = {}; // 48 x 48, one bit per pixel, rows MSB first.
    uint32_t revision = 0;
};

void navBleBegin();
NavData navBleSnapshot();
