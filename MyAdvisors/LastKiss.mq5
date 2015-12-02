//+------------------------------------------------------------------+
//|                                                     LastKiss.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

#include "Zone\ZoneFinder.mqh"
#include "Zone\ZoneDynamicArray.mqh"
#include "Zone\Zone.mqh"
#include "LK.mqh"
#include "BarHelper.mqh"

input int      bellyPips = 30;                     // Belly pips
input int      adjacents = 6;                      // Adjacent bars
input int      turningPoints = 3;                  // Turning points

input int      candlesToWaitOrder = 2;             // Candles to wait for triggering order
input int      minCandlesToWaitSecondTouch=6;      // Candles to wait for kiss (min)
input int      maxCandlesToWaitSecondTouch=25;     // Candles to wait for kiss (max)

input double   tpMultiplier = 9.0;                // TP multiplier on belly
input double   lot=0.1;                            // LOT

int            EA_Magic=12346;                     // EA Magic Number

ZoneFinder *zoneFinder;
LK *lastKiss;
BarHelper *barHelper;

MqlRates previousBar;
MqlTick latestPrice;
MqlTradeRequest mrequest;
MqlTradeResult mresult;

double belly;
long triggerWaitingTime,minWait,maxWait;

int OnInit() {
   Print("=== LastKiss started ===");

   long periodSeconds=PeriodSeconds(_Period);
   belly=bellyPips*_Point;

   triggerWaitingTime = periodSeconds * candlesToWaitOrder;
   minWait =            periodSeconds * minCandlesToWaitSecondTouch;
   maxWait =            periodSeconds * maxCandlesToWaitSecondTouch;
   
   ObjectsDeleteAll(0);

   zoneFinder=new ZoneFinder(belly,adjacents,turningPoints);
   lastKiss=NULL;
   barHelper=new BarHelper;

   if(Bars(_Symbol,_Period) < zoneFinder.numberOfBars) {
      Alert("We don't have enough bars, EA exits now! (", Bars(_Symbol,_Period), ")");
      return(INIT_FAILED);
   } else {
      OnBar();
      return(INIT_SUCCEEDED);
   }
}

void OnDeinit(const int reason) {
   delete(zoneFinder);
   delete(lastKiss);
   delete(barHelper);
}

void OnTick() {
   if(isNewBar()) {
      OnBar();
   }
}


void OnBar() {
   if(!SymbolInfoTick(_Symbol,latestPrice)) {
      Alert("Error getting the latest price quote - error: ",GetLastError());
      return;
   }

   zoneFinder.findZones();

   if (PositionSelect(_Symbol)) {
      return;
   }
    
   fillPreviousBar();
   waitForLastKiss();
   searchBreakOutCandle();
}

void waitForLastKiss() {
   if(lastKiss != NULL) {
      if (previousBar.time >= lastKiss.crossingBar.time + maxWait) {
         Print("Signal is too old: ", lastKiss.crossingBar.time);
         lastKiss = NULL;

      } else if (
         (lastKiss.resistanceCross && previousBar.high < lastKiss.crossedZone.from) || 
         (!lastKiss.resistanceCross && previousBar.low > lastKiss.crossedZone.to)) {
         Print("Signal was fake: ", lastKiss.crossingBar.time);
         lastKiss = NULL;
      } else if (shouldOpenTrade()) {
         if (resistanceKissCandleAppeared()) {
            placeBuyStop();
            lastKiss = NULL;
         } else if (supportKissCandleAppeared()) {
            placeSellStop();
            lastKiss = NULL;
         }
      }
   }
}

bool shouldOpenTrade(){
   return(previousBar.time >= lastKiss.crossingBar.time + minWait);
}

bool resistanceKissCandleAppeared() {
   double crossedClose = lastKiss.crossedZone.price;
   return (lastKiss.resistanceCross && 
           crossedClose < previousBar.close && 
           crossedClose < previousBar.open && 
           lastKiss.crossedZone.to > previousBar.low); // lastKiss.crossedZone.to helyett lastKiss.crossedZone.price?
}

bool supportKissCandleAppeared() {
   double crossedClose = lastKiss.crossedZone.price;
   return (!lastKiss.resistanceCross && 
           crossedClose > previousBar.close && 

           crossedClose > previousBar.open && 
           lastKiss.crossedZone.from < previousBar.high); // lastKiss.crossedZone.from helyett lastKiss.crossedZone.price?
}

void searchBreakOutCandle() {
   if(lastKiss == NULL) {
      ZoneDynamicArray *zones=zoneFinder.getZones();
      for(int i=0; i < zones.size(); i++) {
         Zone zone=zones.elements[i];
         
         if (crosses(zone)) {
            lastKiss = new LK(zone, previousBar, barHelper.isBull(previousBar));
            barHelper.drawArrow(previousBar, "LK_Open", clrWhite);
            return;
         }
      }
   }
}

bool crosses(Zone& zone) {
   return ((previousBar.open < zone.price && previousBar.close > zone.price) ||
           (previousBar.close < zone.price && previousBar.open > zone.price));
}

// ==========================================================

void placeBuyStop() 
{
   ZeroMemory(mresult);
   ZeroMemory(mrequest);
   
   buildRequest();
   
   double high=previousBar.high;
   double stopLossPrice=lastKiss.crossingBar.low - belly;
   double takeProfitPrice=high+belly*tpMultiplier;
   
   mrequest.price =  NormalizeDouble(high,_Digits);                       // latest ask price
   mrequest.sl =     NormalizeDouble(stopLossPrice,_Digits);              // Stop Loss
   mrequest.tp =     NormalizeDouble(takeProfitPrice,_Digits);            // Take Profit
   mrequest.type = ORDER_TYPE_BUY_STOP;                                   // Buy Order
   
   OrderSend(mrequest,mresult);
   explainResult();
}
   
void placeSellStop() 
{
   ZeroMemory(mresult);
   ZeroMemory(mrequest);
   
   buildRequest();
   
   double low=previousBar.low;
   double stopLossPrice=lastKiss.crossingBar.high + belly;
   double takeProfitPrice=low - belly*tpMultiplier;
   
   mrequest.price = NormalizeDouble(low,_Digits);
   mrequest.sl = NormalizeDouble(stopLossPrice,_Digits);
   mrequest.tp = NormalizeDouble(takeProfitPrice,_Digits);
   mrequest.type= ORDER_TYPE_SELL_STOP;
   
   OrderSend(mrequest,mresult);
   explainResult();
}

void buildRequest(){
   mrequest.action=TRADE_ACTION_PENDING;
   mrequest.symbol = _Symbol;
   mrequest.volume = lot;
   mrequest.magic = EA_Magic;
   mrequest.type_filling = ORDER_FILLING_FOK;
   mrequest.deviation=100;
   mrequest.type_time = ORDER_TIME_SPECIFIED;
   mrequest.expiration = latestPrice.time + triggerWaitingTime;
}

void explainResult() {
   if(mresult.retcode==10009 || mresult.retcode==10008) {
      Print("A Sell order has been successfully placed with Ticket#: ",mresult.order,"!");
   } else {
      Print("The Sell order request could not be completed -error: ",GetLastError(), ", ", mresult.comment);
      ResetLastError();
      return;
   }
}

// ==========================================================

bool isNewBar() {
   static datetime last_time=0;
   datetime lastbar_time=SeriesInfoInteger(Symbol(),Period(),SERIES_LASTBAR_DATE);

   if(last_time==0) 
     {
      last_time= lastbar_time;
      return(false);
     }

   if(last_time!=lastbar_time) 
     {
      last_time=lastbar_time;
      return(true);
     }

   return(false);
}

void fillPreviousBar() {
   MqlRates latestBars[];
   
   int copied=CopyRates(_Symbol,_Period,1,1,latestBars);
   
   if(copied<=0) {
      Print("Error copying price data ",GetLastError());
   } else {
      previousBar=latestBars[0];
   }
}

//+------------------------------------------------------------------+

