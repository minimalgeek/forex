//+------------------------------------------------------------------+
//|                                                  MACrossTest.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

#include <Trade\Trade.mqh>

input int fastMA = 5;
input int slowMA = 14;
input double LOT = 0.1;
input int SL = 50;
input int TP = 100;

int MAGIC = 999;

int fastMAHandle;
int slowMAHandle;
double slValue, tpValue;
double slowBuffer[];
double fastBuffer[];

MqlTradeRequest trReq;
MqlTradeResult trRez;
MqlTick latestPrice;

int OnInit()
{
   Print("=== MACrossTest started ===");
   
   if(Bars(_Symbol,_Period) < slowMA) {
   
      Alert("We don't have enough bars, EA exits now! (", Bars(_Symbol,_Period), ")");
      return(INIT_FAILED);
   
   } else {
      fastMAHandle = iMA(_Symbol, _Period, fastMA, 0, MODE_SMA, PRICE_CLOSE);
      slowMAHandle = iMA(_Symbol, _Period, slowMA, 0, MODE_SMA, PRICE_CLOSE);
      
      slValue = SL * _Point;
      tpValue = TP * _Point;
      
      if(_Digits == 5) {
         slValue *= 10;
         tpValue *= 10;
      }
      
      OnBar();
      return(INIT_SUCCEEDED);
   }
}

void OnDeinit(const int reason){
}

void OnTick()
{
   if(isNewBar()) {
      OnBar();
   }
}

// ===================================================

bool isNewBar() {
   static datetime last_time=0;
   datetime lastbar_time = SeriesInfoInteger(_Symbol, _Period, SERIES_LASTBAR_DATE);

   if(last_time == 0) {
      last_time= lastbar_time;
      return(false);
   }

   if(last_time != lastbar_time) {
      last_time=lastbar_time;
      return(true);
   }

   return(false);
}

// ===================================================

void OnBar() {
   if(!SymbolInfoTick(_Symbol,latestPrice)) {
      Alert("Error getting the latest price quote - error: ",GetLastError());
      return;
   }
   
   ZeroMemory(trReq);
   ZeroMemory(trRez);
   
   CopyBuffer(slowMAHandle, 0, 0, 4, slowBuffer);
   CopyBuffer(fastMAHandle, 0, 0, 4, fastBuffer);

   if(slowBuffer[1] > fastBuffer[1] && slowBuffer[2] < fastBuffer[2]) {
      if (PositionSelect(_Symbol)) {
         buy();
      }
   
      buy();
      
   } else if(slowBuffer[1] < fastBuffer[1] && slowBuffer[2] > fastBuffer[2]) {
      if (PositionSelect(_Symbol)) {
         sell();
      }
      
      sell();
      
   }
}

void explainResult() {
   if(trRez.retcode==10009 || trRez.retcode==10008) {
      Print("A Sell order has been successfully placed with Ticket#: ",trRez.order,"!");
   } else {
      Print("The Sell order request could not be completed -error: ",GetLastError(), ", ", trRez.comment);
      ResetLastError();
      return;
   }
}

void buy() {
   double price = latestPrice.ask;
   
   trReq.action            = TRADE_ACTION_DEAL;
   trReq.magic             = MAGIC;
   trReq.symbol            = _Symbol;
   trReq.volume            = LOT;
   trReq.deviation         = 100;
   trReq.type_filling      = ORDER_FILLING_IOC;
   trReq.type_time         = ORDER_TIME_GTC;
   trReq.price             = price;
   //trReq.sl                = price - slValue;
   //trReq.tp                = price + tpValue;
   trReq.type              = ORDER_TYPE_BUY;
   OrderSend(trReq,trRez);
   explainResult();
}

void sell() {
   double price = latestPrice.bid;

   trReq.action            = TRADE_ACTION_DEAL;
   trReq.magic             = MAGIC;
   trReq.symbol            = _Symbol;
   trReq.volume            = LOT;
   trReq.deviation         = 100;
   trReq.type_filling      = ORDER_FILLING_IOC;
   trReq.type_time         = ORDER_TIME_GTC;
   trReq.price             = price;
   //trReq.sl                = price + slValue;
   //trReq.tp                = price - tpValue;
   trReq.type              = ORDER_TYPE_SELL;
   OrderSend(trReq,trRez);
   explainResult();
}