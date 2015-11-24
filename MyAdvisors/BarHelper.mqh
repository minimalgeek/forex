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
   
   bool isBear(MqlRates &bar) 
     {
      return bar.close < bar.open;
     }

   bool isBull(MqlRates &bar) 
     {
      return !isBear(bar);
     }
  };
//+------------------------------------------------------------------+
