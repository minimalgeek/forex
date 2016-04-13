//+------------------------------------------------------------------+
//|                                                     OneStand.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//| Include                                                          |
//+------------------------------------------------------------------+
#include <Trade\Trade.mqh>
//+------------------------------------------------------------------+
//| Inputs                                                           |
//+------------------------------------------------------------------+
input int daysForHHLL = 15;
input int maPeriodFast = 14;
input int maPeriodSlow = 43;
input double lot=0.1;
input int slPip = 300;
input int tpPip = 420;
//+------------------------------------------------------------------+
//| Initialization function of the expert                            |
//+------------------------------------------------------------------+

MqlTradeRequest mrequest;
MqlTradeResult mresult;
MqlRates previousBar;
MqlTick latestPrice;
CTrade trade;
int EA_Magic=12346;                     // EA Magic Number
bool opened = false;

int OnInit()
{
   Print("Unit ready");
   return(INIT_SUCCEEDED);
}
//+------------------------------------------------------------------+
//| Deinitialization function of the expert                          |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{

}
//+------------------------------------------------------------------+
//| "Tick" event handler function                                    |
//+------------------------------------------------------------------+
void OnTick()
{
   datetime    tm = TimeCurrent();
   MqlDateTime stm;
   TimeToStruct(tm,stm);
   
   int day = stm.day_of_week;
   Print("Day of the week: " + day);
   Print("Total nr of positions: " + PositionsTotal());
   
   if (day == 5 && !opened) { // FRIDAY
      double highestHigh = CheckForHighestCandle();
      double lowestLow = CheckForLowestCandle();
      
      double fastMA = iMA(_Symbol, _Period, maPeriodFast, 0, MODE_SMA, PRICE_CLOSE);
      double slowMA = iMA(_Symbol, _Period, maPeriodSlow, 0, MODE_SMA, PRICE_CLOSE);
      
      MqlTick last_tick; 
      SymbolInfoTick(Symbol(),last_tick);
      
      if (fastMA > slowMA) {
         Print("Place BUY: " + highestHigh + ", current price is: " + last_tick.bid);
         opened = trade.BuyStop(lot, highestHigh, _Symbol, highestHigh - slPip*_Point, highestHigh + tpPip*_Point, ORDER_TIME_DAY);
         Print("Success: " + opened);
      }
      
      if (fastMA < slowMA) {
         Print("Place SELL: " + lowestLow + ", current price is: " + last_tick.ask);
         opened = trade.SellStop(lot, lowestLow, _Symbol, lowestLow + slPip*_Point, lowestLow - tpPip*_Point, ORDER_TIME_DAY);
         Print("Success: " + opened);
      }
   }
   
   if (day == 1 && opened) {
      Print("Close position");
      trade.PositionClose(_Symbol, 100);
      opened = false;
   }
}

double CheckForHighestCandle()
{
   double Highs[];
   ArraySetAsSeries(Highs,true);
   CopyHigh(_Symbol,_Period,0,daysForHHLL,Highs);
   int Calc1= ArrayMaximum(Highs,0,daysForHHLL);
   return Highs[Calc1];
}

double CheckForLowestCandle()
{
   double Lows[];
   ArraySetAsSeries(Lows,true);
   CopyLow(_Symbol,_Period,0,daysForHHLL,Lows);
   int Calc1 = ArrayMinimum(Lows,0,daysForHHLL);
   return Lows[Calc1];
}

void placeOrder(double price, ENUM_ORDER_TYPE type) 
{
   ZeroMemory(mresult);
   ZeroMemory(mrequest);
   
   buildRequest();
   Print("...on price: " + price);
   mrequest.price =  NormalizeDouble(price,_Digits);
   mrequest.type = type;
   
   OrderSend(mrequest,mresult);
   explainResult();
}

void buildRequest(){
   mrequest.action=TRADE_ACTION_PENDING;
   mrequest.symbol = _Symbol;
   mrequest.volume = lot;
   mrequest.magic = EA_Magic;
   mrequest.deviation=100;
   mrequest.type_filling = ORDER_FILLING_FOK;
   //mrequest.type_time = ORDER_TIME_SPECIFIED;
   //mrequest.expiration = TimeCurrent()+60*60;
}

void explainResult() {
   if(mresult.retcode==10009 || mresult.retcode==10008) {
      Print("A Sell order has been successfully placed with Ticket#: ",mresult.order,"!");
      opened = true;
   } else {
      Print("The Sell order request could not be completed -error: ",GetLastError(), ", ", mresult.comment);
      ResetLastError();
      opened = false;
      return;
   }
}

//+------------------------------------------------------------------+
//| "Trade" event handler function                                   |
//+------------------------------------------------------------------+
void OnTrade()
{

}
//+------------------------------------------------------------------+
//| "Timer" event handler function                                   |
//+------------------------------------------------------------------+
void OnTimer()
{

}
//+------------------------------------------------------------------+
