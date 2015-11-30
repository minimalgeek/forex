//+------------------------------------------------------------------+
//|                                                    BarHelper.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//|                                                                  |
//+------------------------------------------------------------------+
class BarHelper
  {
private:

public:
   BarHelper(){}
   ~BarHelper(){}
   
   bool isBear(MqlRates &bar) {
      return bar.close < bar.open;
   }

   bool isBull(MqlRates &bar) {
      return !isBear(bar);
   }
     
   void drawArrow(MqlRates& bar, string prefix) {
      bool isBull = isBull(bar);
      string label = prefix + bar.time;
      if (ObjectFind(0, label) < 0) {
         if(!ObjectCreate(0,label,OBJ_ARROW,0,0,0,0,0)) { 
            Print(__FUNCTION__, ": failed to create an arrow! Error code = ",GetLastError()); 
            return; 
         }
         ObjectSetInteger(0,label,OBJPROP_ARROWCODE,isBull ? 241 : 242);
         ObjectSetInteger(0,label,OBJPROP_TIME,bar.time);
         ObjectSetDouble(0,label,OBJPROP_PRICE,isBull ? bar.low : bar.high);
         ChartRedraw(0);
      }
   }
};
//+------------------------------------------------------------------+
