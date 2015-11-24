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

#include <..\Experts\MyAdvisors\MqlRatesDynamicArray.mqh>
#include <..\Experts\MyAdvisors\BarHelper.mqh>

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
   
   double zoneBelly;
   double shrinkedZoneBelly;
   
   MqlRates candles[];
   MqlRatesDynamicArray* localTurningPoints;
   MqlRatesDynamicArray* zones;
   BarHelper* helper;
   
   void findLocalTurningPoints();
   bool identifiableAsNewZone(MqlRates& turningPoint);
   void addZone(MqlRates& bar);

   void drawArrow(MqlRates& bar);
   void drawZoneLines(MqlRates& bar);
   
public:
   static const int numberOfBars;
   
   ZoneFinder(double zb, double szb, int adj, int turnPoint);
   ~ZoneFinder(void);
   
   void findZones();
   MqlRatesDynamicArray* getZones() {
      return(zones);
   }
};

// Constructor and Destructor

ZoneFinder::ZoneFinder(double zb,double szb, int adj, int turnPoint) {
   ArrayResize(candles, numberOfBars);
   ArraySetAsSeries(candles, true);
   
   localTurningPoints = new MqlRatesDynamicArray;
   zones = new MqlRatesDynamicArray;
   helper = new BarHelper;
   
   this.zoneBelly = zb;
   this.shrinkedZoneBelly = szb;
   
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

const int ZoneFinder::numberOfBars = 500;
const int ZoneFinder::maxAllowedSize = 10;

// Functions

ZoneFinder::findZones(void) {
   if(CopyRates(_Symbol, _Period, 0, numberOfBars, candles)<0) {
      Alert("Error copying rates/history data - error:",GetLastError(),"!!");
      ResetLastError();
      return;
   }
   
   findLocalTurningPoints();
   
   for (int i = 0; i < localTurningPoints.Size(); i++) 
   {
      MqlRates turningPoint = localTurningPoints.Element[i];
      int nrOfSimilarPoint = 0;
      
      for (int j = 0; j < localTurningPoints.Size(); j++) {
         MqlRates tempTurningPoint = localTurningPoints.Element[j];
         if (tempTurningPoint.time != turningPoint.time) {
            double difference = MathAbs(tempTurningPoint.close - turningPoint.close);
            if (difference < shrinkedZoneBelly) {
               nrOfSimilarPoint++;

            }
         }
      }
      
      if (nrOfSimilarPoint >= turningPoints && identifiableAsNewZone(turningPoint)) {
         addZone(turningPoint);
      }
   }
}

void ZoneFinder::addZone(MqlRates& bar) {
   Print("Zone added at: " + bar.close);
   if (zones.Size() == maxAllowedSize) {
      MqlRates deletedZone = zones.DeleteLatest();
      string label = "line" + deletedZone.time;
      if (ObjectFind(0, label) >= 0) {
         ObjectDelete(0, label);
         ObjectDelete(0, label + "small");
         ObjectDelete(0, label + "big");
      }
   }
   
   zones.AddValue(bar);
   drawZoneLines(bar);
}

bool ZoneFinder::identifiableAsNewZone(MqlRates& turningPoint) {
   for (int i = 0; i < zones.Size(); i++) {
      MqlRates barToCheck = zones.Element[i];
      double difference = MathAbs(turningPoint.close - barToCheck.close);
      if (difference < zoneBelly) {
         return(false);
      }
 
   }
   
   return(true);
}

ZoneFinder::findLocalTurningPoints(void) {
   localTurningPoints.Clear();
   
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

         localTurningPoints.AddValue(bar);
         drawArrow(bar);
      }
   }
}

ZoneFinder::drawArrow(MqlRates& bar) {
   bool isBull = helper.isBull(bar);
   string text = isBull ? "up_arrow" : "down_arrow";
   text = text + bar.time;
   if (ObjectFind(0, text) < 0) {
      if(!ObjectCreate(0,text,OBJ_ARROW,0,0,0,0,0)) { 
         Print(__FUNCTION__, ": failed to create an arrow! Error code = ",GetLastError()); 
         return; 
      }
      ObjectSetInteger(0,text,OBJPROP_ARROWCODE,isBull ? 241 : 242);
      ObjectSetInteger(0,text,OBJPROP_TIME,bar.time);
      ObjectSetDouble(0,text,OBJPROP_PRICE,isBull ? bar.low : bar.high);
      ChartRedraw(0);
   }
}

ZoneFinder::drawZoneLines(MqlRates& bar) {
   string text = "line" + bar.time;
   if (ObjectFind(0, text) < 0) {
      if(!ObjectCreate(0,text,OBJ_HLINE,0,0,bar.close)) {
         Print(__FUNCTION__, ": failed to create a horizontal line! Error code = ",GetLastError()); 
         return; 
      }
      ObjectSetInteger(0,text,OBJPROP_COLOR,clrRed); 
      
      ObjectCreate(0,text + "small",OBJ_HLINE,0,0,bar.close - shrinkedZoneBelly);
      ObjectSetInteger(0,text + "small",OBJPROP_COLOR,clrAzure); 
      
      ObjectCreate(0,text + "big",OBJ_HLINE,0,0,bar.close + shrinkedZoneBelly);
      ObjectSetInteger(0,text + "big",OBJPROP_COLOR,clrAzure);
      
      ChartRedraw(0);
   }
}