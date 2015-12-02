//+------------------------------------------------------------------+
//|                                              WammieAndMoolah.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

#include "Zone\ZoneFinder.mqh"
#include "Zone\ZoneDynamicArray.mqh"
#include "Zone\Zone.mqh"
#include "WAM.mqh"
#include "BarHelper.mqh"

// setup for EURUSD H1
input int      bellyPips = 70;                     // Belly pips
input int      adjacents = 5;                      // Adjacent bars
input int      turningPoints = 3;                  // Turning points
input int      candlesToWaitOrder = 4;             // Candles to wait for triggering order
input int      minCandlesToWaitSecondTouch=6;      // Min. candles to wait for second touch
input int      maxCandlesToWaitSecondTouch=40;     // Max. candles to wait for second touch
input double   tpMultiplier = 10.0;                // TP multiplier on belly
input double   lot=0.1;                            // LOT

int            EA_Magic=12345;   // EA Magic Number

ZoneFinder *zoneFinder;
WAM *wam;
BarHelper *barHelper;

MqlRates previousBar;
MqlTick latestPrice;
MqlTradeRequest mrequest;
MqlTradeResult mresult;

double belly;
long triggerWaitingTime,minWait,maxWait;

int OnInit()
  {
   Print("=== Wammie started ===");

   long periodSeconds=PeriodSeconds(_Period);
   belly=bellyPips*_Point;

   triggerWaitingTime = periodSeconds * candlesToWaitOrder;
   minWait =            periodSeconds * minCandlesToWaitSecondTouch;
   maxWait =            periodSeconds * maxCandlesToWaitSecondTouch;
   
   ObjectsDeleteAll(0);

   zoneFinder=new ZoneFinder(belly,adjacents,turningPoints);
   wam=NULL;
   barHelper=new BarHelper;

   if(Bars(_Symbol,_Period) < zoneFinder.numberOfBars) {
      Alert("We don't have enough bars, EA exits now!");
      return(INIT_FAILED);
   } else {
      OnBar();
      return(INIT_SUCCEEDED);
   }
}

void OnDeinit(const int reason) {
   delete(zoneFinder);
   delete(wam);
   delete(barHelper);
}

void OnTick() {
   if(isNewBar()) {
      OnBar();
   }
}

// ==================================================================================

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
   findWammieAndMoolah();
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

void findWammieAndMoolah() {
   if(wam==NULL) {
      ZoneDynamicArray *zones=zoneFinder.getZones();
      for(int i=0; i<zones.size(); i++) {
         Zone zone=zones.elements[i];

         if(wammieTouchesTheZone(zone)) {
            barHelper.drawArrow(previousBar, "wammie_1st");
            wam=new WAM;
            wam.wammie=true;
            wam.crossedZone= zone;
            wam.firstTouch = previousBar;
            break;
         }

         if(moolahTouchesTheZone(zone)) {
            barHelper.drawArrow(previousBar, "moolah_1st");
            wam=new WAM;
            wam.wammie=false;
            wam.crossedZone= zone;
            wam.firstTouch = previousBar;
            break;
         }
      }
   } else {
      long firstTouchTime=wam.firstTouch.time;
      
      if(wam.wammie) { // WAMMIE
         if(previousBar.time>firstTouchTime+minWait && 
            previousBar.time<=firstTouchTime+maxWait && 
            wammieTouchesTheZone(wam.crossedZone) && 
            previousBar.low>wam.firstTouch.low) {
            
            barHelper.drawArrow(previousBar, "wammie_2nd");
            wam.secondTouch=previousBar;
         } else if(
            wam.firstTouch.close!=0.0 && 
            wam.secondTouch.close!=0.0 && 
            barHelper.isBull(previousBar)) {
               placeBuyStop();
               wam=NULL;
         } else if(
            previousBar.time>firstTouchTime+maxWait || 
            priceIsBelowTheZone(wam.crossedZone,previousBar.high)) {
               wam=NULL;
         }
         
         
      } else { // MOOLAH
         if(previousBar.time>firstTouchTime+minWait && 
            previousBar.time<=firstTouchTime+maxWait && 
            moolahTouchesTheZone(wam.crossedZone) && 
            previousBar.high>wam.firstTouch.high) {
            
            barHelper.drawArrow(previousBar, "moolah_2nd");
            wam.secondTouch=previousBar;
         } else if(
            wam.firstTouch.close!=0.0 && 
            wam.secondTouch.close!=0.0 && 
            barHelper.isBear(previousBar)) {
               placeSellStop();
               wam=NULL;
         } else if(
            previousBar.time>firstTouchTime+maxWait || 
            priceIsAboveTheZone(wam.crossedZone,previousBar.low)) {
               wam=NULL;
         }
      }
   }
}

bool wammieTouchesTheZone(Zone &zone) {
   return(barHelper.isBear(previousBar) && (priceIsInTheZone(zone, previousBar.low) || priceIsInTheZone(zone, previousBar.close)));
}

bool moolahTouchesTheZone(Zone &zone) {
   return(barHelper.isBull(previousBar) && (priceIsInTheZone(zone, previousBar.high) || priceIsInTheZone(zone, previousBar.open)));
}

bool priceIsBelowTheZone(Zone &zone,double price) {
   return(price < zone.from);
}

bool priceIsInTheZone(Zone &zone,double price) {
   return(price >= zone.from && price <= zone.to);
}

bool priceIsAboveTheZone(Zone &zone,double price) {
   return(price > zone.to);
}
     
// ==========================================================
   
void placeBuyStop() 
{
   ZeroMemory(mresult);
   ZeroMemory(mrequest);
   
   buildRequest();
   
   double high=previousBar.high;
   double stopLossPrice=wam.firstTouch.low;
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
   double stopLossPrice=wam.firstTouch.high;
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

//+------------------------------------------------------------------+
