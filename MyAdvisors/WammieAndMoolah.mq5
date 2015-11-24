//+------------------------------------------------------------------+
//|                                              WammieAndMoolah.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

#include <..\Experts\MyAdvisors\ZoneFinder.mqh>
#include <..\Experts\MyAdvisors\WAM.mqh>
#include <..\Experts\MyAdvisors\BarHelper.mqh>

// setup for EURUSD H1
input int      bellyPips = 700;                   // Belly pips
input double   zoneShrinkPercentage = 0.1;        // Zone shrink percentage
input int      adjacents = 5;                      // Adjacent bars
input int      turningPoints = 3;                  // Turning points
input int      candlesToWaitOrder = 5;             // Candles to wait for triggering order
input int      minCandlesToWaitSecondTouch = 6;
    // Min. candles to wait for second touch
input int      maxCandlesToWaitSecondTouch = 25;
   // Max. candles to wait for second touch
input double   tpMultiplier = 1.2;                 // TP multiplier on belly

ZoneFinder* zoneFinder;
WAM* wam;
BarHelper* barHelper;
MqlRates previousBar;
double belly, shrinkedZoneBelly;
bool buyOpened, sellOpened;
long triggerWaitingTime, minWait, maxWait;

int OnInit()
{
   Print("=== Wammie started ===");
   
   long periodSeconds = PeriodSeconds(_Period);
   belly = bellyPips*_Point;
   shrinkedZoneBelly = zoneShrinkPercentage*belly;
   
   triggerWaitingTime = periodSeconds * candlesToWaitOrder;
   minWait = periodSeconds * minCandlesToWaitSecondTouch;
   maxWait = periodSeconds * maxCandlesToWaitSecondTouch;
   
   if (ObjectsDeleteAll(0) > 0) {
      Print("Drawn objects successfully removed!");
   }
   
   zoneFinder = new ZoneFinder(belly, shrinkedZoneBelly, adjacents, turningPoints);
   wam = NULL;
   barHelper = new BarHelper;
   
   if(Bars(_Symbol,_Period) < zoneFinder.numberOfBars) {
      Alert("We don't have enough bars, EA exits now!");
      return(INIT_FAILED);
   } else {
      OnBar();
      return(INIT_SUCCEEDED);
   }
}

void OnDeinit(const int reason)
{
   delete(zoneFinder);
   delete(wam);
   delete(barHelper);
}

void OnTick()
{
   if(isNewBar()) {
      OnBar();
   }
}


// ==================================================================================

void OnBar() {

   MqlTick latest_price;
   MqlTradeRequest mrequest;
   MqlTradeResult mresult;
   ZeroMemory(mresult);
   ZeroMemory(mrequest);
   
   if(!SymbolInfoTick(_Symbol,latest_price)) {
      Alert("Error getting the latest price quote - error: ",GetLastError());
      return;
   }
   
   zoneFinder.findZones();
   
   checkOpenedPositions();
   if (buyOpened || sellOpened) {
      return;
   }
   
   fillPreviousBar();
   findWammieAndMoolah();
}

void fillPreviousBar() {
   MqlRates latestBars[];
   
   int copied=CopyRates(_Symbol,_Period,1,1,latestBars);
   
   if(copied<=0){
      Print("Error copying price data ",GetLastError());
   } else {
      previousBar = latestBars[0];
   }
}

void findWammieAndMoolah() {
   if (wam == NULL) {
      MqlRatesDynamicArray* zones = zoneFinder.getZones();
      for (int i = 0; i < zones.Size(); i++) {
         MqlRates zone = zones.Element[i];
         
         if (wammieTouchesTheZone(zone)) {
            wam = new WAM;
            wam.wammie = true;
            wam.crossedZone = zone;
            wam.firstTouch = previousBar;
            break;
         }
         
         if (moolahTouchesTheZone(zone)) {
            wam = new WAM;
            wam.wammie = false;
            wam.crossedZone = zone;
            wam.firstTouch = previousBar;
            break;
         }
      }
   } else {
      long firstTouchTime = wam.firstTouch.time;
      if (wam.wammie) { // WAMMIE
         if (previousBar.time > firstTouchTime + minWait && 
             previousBar.time <= firstTouchTime + maxWait && 
             wammieTouchesTheZone(wam.crossedZone) &&

             previousBar.low > wam.firstTouch.low) {
             
            wam.secondTouch = previousBar;
         } else if (wam.firstTouch.close != 0.0 && 
                    wam.secondTouch.close != 0.0 &&
                    barHelper.isBull(previousBar)) {
             //placeBuyStop();

             wam = NULL;
         } else if (previousBar.time > firstTouchTime + maxWait || 
                    priceIsBelowTheZone(wam.crossedZone, previousBar.high)) {
             wam = NULL;
         }
      } else { // MOOLAH
      
      }
   }
}

bool wammieTouchesTheZone(MqlRates& zone) {
   return(
            (priceIsInTheZone(zone, previousBar.low) || priceIsInTheZone(zone, previousBar.close)) &&
           !(priceIsInTheZone(zone, previousBar.high) || priceIsInTheZone(zone, previousBar.open))
         );
}

bool moolahTouchesTheZone(MqlRates& zone) {
   return(
            (priceIsInTheZone(zone, previousBar.high) || priceIsInTheZone(zone, previousBar.open)) &&
           !(priceIsInTheZone(zone, previousBar.low) || priceIsInTheZone(zone, previousBar.close))
         );
}

bool priceIsBelowTheZone(MqlRates& zone, double price) {
   return(price < zone.close - shrinkedZoneBelly);
}

bool priceIsInTheZone(MqlRates& zone, double price) {
   return(price >= zone.close - shrinkedZoneBelly && price <= zone.close - shrinkedZoneBelly);
}

bool priceIsAboveTheZone(MqlRates& zone, double price) {
   return(price > zone.close + shrinkedZoneBelly);
}

// ==========================================================

bool isNewBar()
{
   static datetime last_time=0;
   datetime lastbar_time=SeriesInfoInteger(Symbol(),Period(),SERIES_LASTBAR_DATE);

   if(last_time == 0) {
      last_time = lastbar_time;
      return(false);
   }

   if(last_time != lastbar_time) {
      last_time = lastbar_time;
      return(true);
   }
   
   return(false);
}

void checkOpenedPositions() {
   buyOpened = false;
   sellOpened = false;
    
   if (PositionSelect(_Symbol) ==true) {
      if (PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY) {
         buyOpened = true;
      } else if(PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_SELL) {
         sellOpened = true;
      }
   }
}
