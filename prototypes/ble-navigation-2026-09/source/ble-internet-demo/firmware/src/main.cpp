#include <Arduino.h>
#include <TFT_eSPI.h>
#include <XPT2046_Touchscreen.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <esp_system.h>

// Nordic UART UUIDs; requests are 11 bytes, replies newline framed ASCII.
static const char *SERVICE="6e400001-b5a3-f393-e0a9-e50e24dcca9e";
static const char *RX_UUID="6e400002-b5a3-f393-e0a9-e50e24dcca9e";
static const char *TX_UUID="6e400003-b5a3-f393-e0a9-e50e24dcca9e";
TFT_eSPI screen;
SPIClass touchSPI(VSPI);
XPT2046_Touchscreen touch(33,36);
BLECharacteristic *outgoing;
BLE2902 *subscription;
portMUX_TYPE mux=portMUX_INITIALIZER_UNLOCKED;
volatile bool connected=false,dirty=true;
char readyLine[200]={0};
uint8_t requestPacket[11];
uint32_t sessionId,sequence=0,lastSend=0,lastPoll=0;
bool pending=false,wasPressed=false;
int attempts=0;
String message="Waiting for phone",statusText="Pair in Android app",counts="0 / 0 / 0",revision="-";
uint16_t background=0x10c4;
uint32_t phoneSeconds=0,phoneSyncMillis=0,lastClockSecond=UINT32_MAX;
bool phoneClockValid=false;

bool syncPhoneClock(const char *value) {
 if(!value||strlen(value)!=8||value[2]!='.'||value[5]!='.')return false;
 for(int i=0;i<8;i++)if(i!=2&&i!=5&&(value[i]<'0'||value[i]>'9'))return false;
 unsigned h=(value[0]-'0')*10+value[1]-'0',m=(value[3]-'0')*10+value[4]-'0',s=(value[6]-'0')*10+value[7]-'0';
 if(h>23||m>59||s>59)return false;
 phoneSeconds=h*3600+m*60+s;phoneSyncMillis=millis();phoneClockValid=true;return true;
}
String phoneClock() {
 if(!phoneClockValid||!connected)return "--.--.--";
 uint32_t t=(phoneSeconds+(uint32_t)(millis()-phoneSyncMillis)/1000)%86400;
 char value[9];snprintf(value,sizeof(value),"%02u.%02u.%02u",(unsigned)(t/3600),(unsigned)(t/60%60),(unsigned)(t%60));return String(value);
}

class ServerCallbacks:public BLEServerCallbacks {
 void onConnect(BLEServer*)override {connected=true;dirty=true;Serial.println("BLE connected");}
 void onDisconnect(BLEServer*)override {connected=false;subscription->setNotifications(false);dirty=true;BLEDevice::startAdvertising();Serial.println("BLE disconnected; advertising");}
};
class ReceiveCallbacks:public BLECharacteristicCallbacks {
 std::string assembling;
 void onWrite(BLECharacteristic *c)override {
   for(char ch:c->getValue()) {
     if(ch=='\r') {assembling.clear();}
     else if(ch=='\n') {
       portENTER_CRITICAL(&mux);
       strlcpy(readyLine,assembling.c_str(),sizeof(readyLine));
       portEXIT_CRITICAL(&mux);
       assembling.clear();
     } else assembling+=ch;
     if(assembling.size()>190) assembling.clear();
   }
 }
};
void draw() {
 screen.fillScreen(background);
 screen.setTextColor(TFT_WHITE,background);screen.drawString("BLE INTERNET DEMO",12,8,2);
 screen.drawRightString(phoneClock(),308,8,2);
 screen.setTextColor(connected?TFT_GREEN:TFT_ORANGE,background);
 screen.drawString(connected?"PHONE CONNECTED":"WAITING FOR PHONE",12,34,2);
 screen.setTextColor(TFT_WHITE,background);
 screen.drawString(message.substring(0,25),12,65,2);
 screen.drawString(message.substring(25),12,87,2);
 screen.drawString("Counts: "+counts,12,114,2);
 screen.setTextColor(TFT_LIGHTGREY,background);
 screen.drawString(statusText.substring(0,38),12,139,2);
 for(int i=0;i<3;i++) {
   int x=8+i*105;screen.fillRoundRect(x,174,96,57,8,pending?TFT_DARKGREY:TFT_DARKCYAN);
   screen.setTextColor(TFT_WHITE);screen.drawCentreString("SEND "+String(i+1),x+48,193,2);
 }
}
void transmit() {
 outgoing->setValue(requestPacket,sizeof(requestPacket));outgoing->notify();lastSend=millis();attempts++;
}
void request(uint8_t button) {
 if(pending||!connected||!subscription->getNotifications())return;
 sequence++;requestPacket[0]=1;requestPacket[1]=button?2:1;
 memcpy(requestPacket+2,&sessionId,4);memcpy(requestPacket+6,&sequence,4);requestPacket[10]=button;
 pending=true;attempts=0;statusText=button?"Sending button...":"Reading server...";transmit();dirty=true;
}
void setup() {
 Serial.begin(115200);sessionId=esp_random();
 screen.init();screen.setRotation(1);pinMode(21,OUTPUT);digitalWrite(21,HIGH);
 touchSPI.begin(25,39,32,33);touch.begin(touchSPI);touch.setRotation(1);
 BLEDevice::init("CYD-Internet-Demo");
 auto *server=BLEDevice::createServer();server->setCallbacks(new ServerCallbacks());
 auto *service=server->createService(SERVICE);
 outgoing=service->createCharacteristic(TX_UUID,BLECharacteristic::PROPERTY_NOTIFY);
 subscription=new BLE2902();outgoing->addDescriptor(subscription);
 auto *incoming=service->createCharacteristic(RX_UUID,BLECharacteristic::PROPERTY_WRITE);
 incoming->setCallbacks(new ReceiveCallbacks());service->start();
 auto *advertising=BLEDevice::getAdvertising();advertising->addServiceUUID(SERVICE);advertising->setScanResponse(true);advertising->start();
 Serial.printf("DEMO READY: CYD-Internet-Demo; session=%08lx; free_heap=%u\n",(unsigned long)sessionId,ESP.getFreeHeap());
 draw();
}
void loop() {
 static uint32_t lastHeartbeat=0;
 if(millis()-lastHeartbeat>10000){lastHeartbeat=millis();Serial.printf("DEMO ALIVE: BLE=%d subscribed=%d pending=%d heap=%u\n",connected,subscription->getNotifications(),pending,ESP.getFreeHeap());}
 char line[200];portENTER_CRITICAL(&mux);strlcpy(line,readyLine,sizeof(line));readyLine[0]=0;portEXIT_CRITICAL(&mux);
 if(line[0]) {
   // seq and separators cannot be supplied by webpage text (server validates it).
   char *save=nullptr;char *kind=strtok_r(line,"|",&save);char *seq=strtok_r(nullptr,"|",&save);
   if(pending&&seq&&strtoul(seq,nullptr,10)==sequence) {
     if(strcmp(kind,"S")==0) {
       char *rev=strtok_r(nullptr,"|",&save),*c=strtok_r(nullptr,"|",&save),*m=strtok_r(nullptr,"|",&save),*phoneTime=strtok_r(nullptr,"|",&save);
       if(rev&&c&&m){revision=rev;counts=c;message=m;syncPhoneClock(phoneTime);statusText="Server OK / revision "+revision;pending=false;lastPoll=millis();}
     } else if(strcmp(kind,"E")==0) {statusText="HTTP error / will retry";/* same id on retry */}
     dirty=true;
   }
 }
 if(pending&&millis()-lastSend>12000) {
   if(attempts<3&&connected&&subscription->getNotifications())transmit();
   else {pending=false;statusText="No ACK; result unknown";lastPoll=millis();dirty=true;}
 }
 bool pressed=touch.touched();
 if(pressed&&!wasPressed) {
   TS_Point p=touch.getPoint();
   // CYD typical calibration. Adjust endpoints if touch targets are shifted.
   int x=constrain(map(p.x,200,3700,0,319),0,319);
   int y=constrain(map(p.y,240,3800,0,239),0,239);
   Serial.printf("touch raw=%d,%d pixel=%d,%d\n",p.x,p.y,x,y);
   if(y>=174&&x>=8&&x<314)request(constrain((x-8)/105+1,1,3));
 }
 wasPressed=pressed;
 if(!pending&&millis()-lastPoll>3000)request(0);
 if(phoneClockValid&&connected){uint32_t second=(millis()-phoneSyncMillis)/1000;if(second!=lastClockSecond){lastClockSecond=second;dirty=true;}}
 if(dirty){dirty=false;draw();}
 delay(5);
}
