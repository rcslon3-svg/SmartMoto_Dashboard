#include "NavBle.h"

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <cstring>

namespace {
constexpr char kServiceUuid[] = "a4aa19e0-c1ec-4f71-96fe-b5750060cc01";
constexpr char kWriteUuid[] = "a4aa19e0-c1ec-4f71-96fe-b5750060cc02";
constexpr uint32_t kFullMask = (1UL << 18) - 1;
portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;
NavData active;
NavData staging;
uint8_t sequence = 0;
uint32_t receivedMask = 0;
bool staged = false;

class ServerCallbacks final : public BLEServerCallbacks {
    void onConnect(BLEServer *) override { Serial.println("nav_ble=connected"); }
    void onDisconnect(BLEServer *) override {
        portENTER_CRITICAL(&mux);
        active.valid = false;
        ++active.revision;
        staged = false;
        portEXIT_CRITICAL(&mux);
        BLEDevice::startAdvertising();
        Serial.println("nav_ble=disconnected");
    }
};

class WriteCallbacks final : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *characteristic) override {
        const std::string value = characteristic->getValue();
        const auto *data = reinterpret_cast<const uint8_t *>(value.data());
        const size_t size = value.size();
        if (size == 1 && data[0] == 'C') {
            portENTER_CRITICAL(&mux);
            active.valid = false;
            ++active.revision;
            staged = false;
            portEXIT_CRITICAL(&mux);
            return;
        }
        if (size >= 5 && data[0] == 'N') {
            const uint8_t turnLength = data[3], remainingLength = data[4];
            if (turnLength > 8 || remainingLength > 8 ||
                size != static_cast<size_t>(5 + turnLength + remainingLength)) return;
            NavData next;
            next.hasIcon = (data[2] & 1) != 0;
            memcpy(next.turn, data + 5, turnLength);
            memcpy(next.remaining, data + 5 + turnLength, remainingLength);
            portENTER_CRITICAL(&mux);
            staging = next;
            sequence = data[1];
            receivedMask = 0;
            staged = true;
            portEXIT_CRITICAL(&mux);
            return;
        }
        if (size == 20 && data[0] == 'I' && data[2] < 18) {
            portENTER_CRITICAL(&mux);
            if (staged && staging.hasIcon && data[1] == sequence) {
                memcpy(staging.icon + data[2] * 16, data + 4, 16);
                receivedMask |= 1UL << data[2];
            }
            portEXIT_CRITICAL(&mux);
            return;
        }
        if (size == 2 && data[0] == 'E') {
            bool committed = false;
            char turn[9] = {}, remaining[9] = {};
            bool icon = false;
            portENTER_CRITICAL(&mux);
            if (staged && data[1] == sequence &&
                (!staging.hasIcon || receivedMask == kFullMask)) {
                staging.valid = true;
                staging.revision = active.revision + 1;
                active = staging;
                staged = false;
                memcpy(turn, active.turn, sizeof(turn));
                memcpy(remaining, active.remaining, sizeof(remaining));
                icon = active.hasIcon;
                committed = true;
            }
            portEXIT_CRITICAL(&mux);
            if (committed) Serial.printf("nav_rx turn=%s remaining=%s icon=%u\n",
                                         turn, remaining, icon);
        }
    }
};
ServerCallbacks serverCallbacks;
WriteCallbacks writeCallbacks;
} // namespace

void navBleBegin() {
    BLEDevice::init("MotoNav-Round");
    BLEServer *server = BLEDevice::createServer();
    server->setCallbacks(&serverCallbacks);
    BLEService *service = server->createService(kServiceUuid);
    BLECharacteristic *write = service->createCharacteristic(
        kWriteUuid, BLECharacteristic::PROPERTY_WRITE);
    write->setCallbacks(&writeCallbacks);
    service->start();
    BLEAdvertising *advertising = BLEDevice::getAdvertising();
    advertising->addServiceUUID(kServiceUuid);
    advertising->setScanResponse(true);
    BLEDevice::startAdvertising();
    Serial.println("nav_ble=advertising name=MotoNav-Round");
}

NavData navBleSnapshot() {
    portENTER_CRITICAL(&mux);
    NavData copy = active;
    portEXIT_CRITICAL(&mux);
    return copy;
}
