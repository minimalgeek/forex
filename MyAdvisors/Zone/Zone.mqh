//+------------------------------------------------------------------+
//|                                                         Zone.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//|                                                                  |
//+------------------------------------------------------------------+
struct Zone {

   double price;
   double from;
   double to;

   datetime startingTime;

   int strength;
   
};