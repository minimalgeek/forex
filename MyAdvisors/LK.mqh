//+------------------------------------------------------------------+
//|                                                          WAM.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//|                                                                  |
//+------------------------------------------------------------------+

#include "Zone\Zone.mqh"

class LK {

private:
public:
     
   Zone              crossedZone;
   MqlRates          crossingBar;
   bool              resistanceCross; // true: resistance, false: support
   
                     LK(Zone& z, MqlRates& bar, bool rc){
                        this.crossedZone = z;
                        this.crossingBar = bar;
                        this.resistanceCross = rc;
                     }
                     
                     ~LK(){
                     
                     }
};
