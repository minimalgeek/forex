//+------------------------------------------------------------------+
//|                                                         Test.mq5 |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

//--- input parameters
input int      StopLoss=30;      // Stop Loss
input int      TakeProfit=100;   // Take Profit
input int      MA_Period=8;      // Moving Average Period
input int      MA_Period_2=20;      // Moving Average Period
input int      EA_Magic=12345;   // EA Magic Number
input double   Lot=0.1;          // Lots to Trade

int maHandle, maHandle2;  // handle for our Moving Average indicator
double maVal[], maVal2[]; // Dynamic array to hold the values of Moving Average for each bars
double p_close; // Variable to store the close value of a bar
int STP, TKP;   // To be used for Stop Loss & Take Profit values
//+------------------------------------------------------------------+
//| Expert initialization function                                   |
//+------------------------------------------------------------------+
int OnInit()
  {
   maHandle=iMA(_Symbol,_Period,MA_Period,0,MODE_EMA,PRICE_CLOSE);
   maHandle2=iMA(_Symbol,_Period,MA_Period_2,0,MODE_EMA,PRICE_CLOSE);

   STP = StopLoss;
   TKP = TakeProfit;

   return(0);
  }
//+------------------------------------------------------------------+
//| Expert deinitialization function                                 |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
  {
   IndicatorRelease(maHandle);
   IndicatorRelease(maHandle2);
  }
//+------------------------------------------------------------------+
//| Expert tick function                                             |
//+------------------------------------------------------------------+
void OnTick()
  {

   static datetime Old_Time;
   datetime New_Time[1];
   bool IsNewBar=false;

// copying the last bar time to the element New_Time[0]
   int copied=CopyTime(_Symbol,_Period,0,1,New_Time);
   if(copied>0) // ok, the data has been copied successfully
     {
      if(Old_Time!=New_Time[0]) // if old time isn't equal to new bar time
        {
         IsNewBar=true;   // if it isn't a first call, the new bar has appeared
         if(MQL5InfoInteger(MQL5_DEBUGGING)) Print("We have new bar here ",New_Time[0]," old time was ",Old_Time);
         Old_Time=New_Time[0];            // saving bar time
        }
     }
   else
     {
      Alert("Error in copying historical times data, error =",GetLastError());
      ResetLastError();
      return;
     }

//--- EA should only check for new trade if we have a new bar
   if(IsNewBar==false)
     {
      return;
     }

//--- Do we have enough bars to work with
   int Mybars=Bars(_Symbol,_Period);
   if(Mybars<60) // if total bars is less than 60 bars
     {
      Alert("We have less than 60 bars, EA will now exit!!");
      return;
     }

//--- Define some MQL5 Structures we will use for our trade
   MqlTick latest_price;      // To be used for getting recent/latest price quotes
   MqlTradeRequest mrequest;  // To be used for sending our trade requests
   MqlTradeResult mresult;    // To be used to get our trade results
   MqlRates mrate[];          // To be used to store the prices, volumes and spread of each bar
   ZeroMemory(mresult);
   ZeroMemory(mrequest);      // Initialization of mrequest structure

                              // the MA-8 values arrays
   ArraySetAsSeries(maVal,true);
   ArraySetAsSeries(maVal2,true);

   CopyRates(_Symbol,_Period,0,3,mrate);
   CopyBuffer(maHandle,0,0,3,maVal);
   CopyBuffer(maHandle2,0,0,3,maVal2);

   bool Buy_opened=false;  // variable to hold the result of Buy opened position
   bool Sell_opened=false; // variables to hold the result of Sell opened position

   if(PositionSelect(_Symbol)==true) // we have an opened position
     {
      if(PositionGetInteger(POSITION_TYPE)==POSITION_TYPE_BUY)
        {
         Buy_opened=true;  //It is a Buy
        }
      else if(PositionGetInteger(POSITION_TYPE)==POSITION_TYPE_SELL)
        {
         Sell_opened=true; // It is a Sell
        }
     }

   p_close=mrate[1].close;  // bar 1 close price

/*
    1. Check for a long/Buy Setup : MA-8 increasing upwards, 
    previous price close above it, ADX > 22, +DI > -DI
*/
//--- Declare bool type variables to hold our Buy Conditions
   bool Buy_Condition_1=(maVal[0]>maVal[1]) && (maVal2[0]<maVal2[1]); // MA-8 Increasing upwards
//--- Putting all together   
   if(Buy_Condition_1)
     {

      if(Buy_opened)
        {
         Alert("We already have a Buy Position!!!");
         return;    // Don't open a new Buy Position
        }
      ZeroMemory(mresult);
      ZeroMemory(mrequest);
      mrequest.action = TRADE_ACTION_DEAL;                                  // immediate order execution
      mrequest.price = NormalizeDouble(latest_price.ask,_Digits);           // latest ask price
      mrequest.symbol = _Symbol;                                            // currency pair
      mrequest.volume = Lot;                                                 // number of lots to trade
      mrequest.magic = EA_Magic;                                             // Order Magic Number
      mrequest.type = ORDER_TYPE_BUY;                                        // Buy Order
      mrequest.type_filling = ORDER_FILLING_IOC;                             // Order execution type
      mrequest.deviation=100;                                                // Deviation from current price
      //--- send order
      OrderSend(mrequest,mresult);
      // get the result code
      if(mresult.retcode==10009 || mresult.retcode==10008) //Request is completed or order placed
        {
         Alert("A Buy order has been successfully placed with Ticket#:",mresult.order,"!!");
        }
      else
        {
         Alert("The Buy order request could not be completed -error:",GetLastError());
         ResetLastError();
         return;
        }
     }
/*
    2. Check for a Short/Sell Setup : MA-8 decreasing downwards, 
    previous price close below it, ADX > 22, -DI > +DI
*/
//--- Declare bool type variables to hold our Sell Conditions
   bool Sell_Condition_1=(maVal[0]<maVal[1]) && (maVal2[0]>maVal2[1]); // MA-8 Increasing upwards

//--- Putting all together
   if(Sell_Condition_1)
     {
      // any opened Sell position?
      if(Sell_opened)
        {
         Alert("We already have a Sell position!!!");
         return;    // Don't open a new Sell Position
        }
      ZeroMemory(mresult);
      ZeroMemory(mrequest);
      mrequest.action=TRADE_ACTION_DEAL;                                // immediate order execution
      mrequest.price = NormalizeDouble(latest_price.bid,_Digits);           // latest Bid price
      mrequest.symbol = _Symbol;                                          // currency pair
      mrequest.volume = Lot;                                              // number of lots to trade
      mrequest.magic = EA_Magic;                                          // Order Magic Number
      mrequest.type= ORDER_TYPE_SELL;                                     // Sell Order
      mrequest.type_filling = ORDER_FILLING_IOC;                          // Order execution type
      mrequest.deviation=100;                                             // Deviation from current price
      //--- send order
      OrderSend(mrequest,mresult);
      // get the result code
      if(mresult.retcode==10009 || mresult.retcode==10008) //Request is completed or order placed
        {
         Alert("A Sell order has been successfully placed with Ticket#:",mresult.order,"!!");
        }
      else
        {
         Alert("The Sell order request could not be completed -error:",GetLastError());
         ResetLastError();
         return;
        }
     }

   return;
  }
//+------------------------------------------------------------------+
