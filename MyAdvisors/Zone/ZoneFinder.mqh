//+------------------------------------------------------------------+
//|                                                   ZoneFinder.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//|                                                                  |
//+------------------------------------------------------------------+

#include "ZoneDynamicArray.mqh"
#include "Zone.mqh"
#include <..\Experts\MyAdvisors\BarHelper.mqh>
#include <..\Experts\MyAdvisors\MqlRatesDynamicArray.mqh>

class ZoneFinder
{
private:

   static const string H_LINE;
   static const string CIRCLE;
   static const string SMALL;
   static const string BIG;
   
   int adjacents;
   int turningPoints;
   static const int maxAllowedSize;

   string symbol;
   long period;
   
   double zoneRange;
   
   MqlRates candles[];
   MqlRatesDynamicArray* localTurningPoints;
   ZoneDynamicArray* zones;
   BarHelper* helper;
   
   void findLocalTurningPoints();
   bool identifiableAsNewZone(MqlRates& turningPoint);
   void addZone(MqlRates& bar, int nrOfSimilarPoint);

   void drawArrow(MqlRates& bar);
   void drawZoneLines(Zone& bar);
   
public:
   static const int numberOfBars;
   
   ZoneFinder(double zr, int adj, int turnPoint);
   ~ZoneFinder(void);
   
   void findZones();
   ZoneDynamicArray* getZones() {
      return(zones);
   }
};

// Constructor and Destructor

ZoneFinder::ZoneFinder(double zr, int adj, int turnPoint) {
   ArrayResize(candles, numberOfBars);
   ArraySetAsSeries(candles, true);
   
   localTurningPoints = new MqlRatesDynamicArray;
   zones = new ZoneDynamicArray;
   helper = new BarHelper;
   
   this.zoneRange = zr;
   
   this.adjacents = adj;
   this.turningPoints = turnPoint;
}

ZoneFinder::~ZoneFinder()
{
   delete(localTurningPoints);
   delete(zones);
   delete(helper);
}

// Constant values

const string ZoneFinder::H_LINE = "hLine";
const string ZoneFinder::CIRCLE = "circle";
const string ZoneFinder::SMALL = "small";
const string ZoneFinder::BIG = "big";

const int ZoneFinder::numberOfBars = 258;
const int ZoneFinder::maxAllowedSize = 10;

// Functions

ZoneFinder::findZones(void) {
   if(CopyRates(_Symbol, _Period, 0, numberOfBars, candles)<0) {
      Alert("Error copying rates/history data - error: ", GetLastError());
      ResetLastError();
      return;
   }
   
   findLocalTurningPoints();
   
   for (int i = 0; i < localTurningPoints.size(); i++) 
   {
      MqlRates turningPoint = localTurningPoints.elements[i];
      int nrOfSimilarPoint = 0;
      
      for (int j = 0; j < localTurningPoints.size(); j++) {
         MqlRates tempTurningPoint = localTurningPoints.elements[j];
         if (tempTurningPoint.time != turningPoint.time) {
            double difference = MathAbs(tempTurningPoint.close - turningPoint.close);
            if (difference < zoneRange) {
               nrOfSimilarPoint++;
            }
         }
      }
      
      if (nrOfSimilarPoint >= turningPoints && identifiableAsNewZone(turningPoint)) {
         addZone(turningPoint, nrOfSimilarPoint);
      }
   }
}

void ZoneFinder::addZone(MqlRates& bar, int nrOfSimilarPoint) {
   if (zones.size() == maxAllowedSize) {
      Zone deletedZone = zones.deleteLatest();
      string label = H_LINE + deletedZone.startingTime;
      if (ObjectFind(0, label) >= 0) {
         ObjectDelete(0, label);
         ObjectDelete(0, label + SMALL);
         ObjectDelete(0, label + BIG);
      }
   }
   
   Zone zone;
   zone.price = bar.close;
   zone.from = bar.close - zoneRange;
   zone.to = bar.close + zoneRange;
   zone.startingTime = bar.time;
   zone.strength = nrOfSimilarPoint;
   zones.addValue(zone);
   Print("Zone added at: ", bar.close, " with strength ", zone.strength);
   drawZoneLines(zone);
}

bool ZoneFinder::identifiableAsNewZone(MqlRates& turningPoint) {
   for (int i = 0; i < zones.size(); i++) {
      Zone zoneToCheck = zones.elements[i];
      if (turningPoint.close > zoneToCheck.from && turningPoint.close < zoneToCheck.to) {
         return(false);
      }
 
   }
   
   return(true);
}

ZoneFinder::findLocalTurningPoints(void) {
   localTurningPoints.clear();
   
   for(int i = adjacents + 1; i < ArraySize(candles) - adjacents; i++) {
      MqlRates bar = candles[i];
      double closePrice = bar.close;
      
      bool smallerTP = true, biggerTP = true;
      
      for (int j = 1; j <= adjacents; j++) {
         MqlRates tempBarPrev = candles[i - j];
         MqlRates tempBarNext = candles[i + j];
         if (tempBarPrev.close < closePrice || tempBarNext.close < closePrice) {
            smallerTP = false;
         }
         
         if (tempBarPrev.close > closePrice || tempBarNext.close > closePrice) {
            biggerTP = false;
         }
      }
      
      if (smallerTP || biggerTP) {
         localTurningPoints.addValue(bar);
         helper.drawArrow(bar, "localTP");
      }
   }
}

ZoneFinder::drawZoneLines(Zone& zone) {
   string text = H_LINE + zone.startingTime;
   if (ObjectFind(0, text) < 0) {
      if(!ObjectCreate(0,text,OBJ_HLINE,0,0,zone.price)) {
         Print(__FUNCTION__, ": failed to create a horizontal line! Error code = ",GetLastError()); 
         return; 
      }
      ObjectSetInteger(0,text,OBJPROP_COLOR,clrRed); 
      
      ObjectCreate(0,text + SMALL,OBJ_HLINE,0,0,zone.from);
      ObjectSetInteger(0,text + SMALL,OBJPROP_COLOR,clrAzure); 
      
      ObjectCreate(0,text + BIG,OBJ_HLINE,0,0,zone.to);
      ObjectSetInteger(0,text + BIG,OBJPROP_COLOR,clrAzure);
      
      ChartRedraw(0);
   }
}