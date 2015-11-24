//+------------------------------------------------------------------+
//|                                         MqlRatesDynamicArray.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"
//+------------------------------------------------------------------+
//|                                                                  |
//+------------------------------------------------------------------+
class MqlRatesDynamicArray
  {
private:
   int               m_ChunkSize;    // Chunk size
   int               m_ReservedSize; // Actual size of the array
   int               m_Size;         // Number of active elements in the array
public:
   MqlRates          Element[];      // The array proper. It is located in the public section, 
                                     // so that we can use it directly, if necessary
   //+------------------------------------------------------------------+
   //|   Constructor                                                    |
   //+------------------------------------------------------------------+
   void MqlRatesDynamicArray(int ChunkSize=1024)
     {
      m_Size=0;                            // Number of active elements
      m_ChunkSize=ChunkSize;               // Chunk size
      m_ReservedSize=ChunkSize;            // Actual size of the array
      ArrayResize(Element,m_ReservedSize); // Prepare the array
     }
   //+------------------------------------------------------------------+
   //|   Function for adding an element at the end of array             |
   //+------------------------------------------------------------------+
   void AddValue(MqlRates &Value)
     {
      m_Size++; // Increase the number of active elements
      if(m_Size>m_ReservedSize)
        { // The required number is bigger than the actual array size
         m_ReservedSize+=m_ChunkSize; // Calculate the new array size
         ArrayResize(Element,m_ReservedSize); // Increase the actual array size
        }
      Element[m_Size-1]=Value; // Add the value
     }
     
   //+------------------------------------------------------------------+
   //|   Function for adding an element at the end of array             |
   //+------------------------------------------------------------------+
   MqlRates DeleteLatest()
     {
      m_Size--;
      MqlRates latest = Element[0];
      ArrayCopy(Element,Element,0,1);
      return(latest);
     }

   //+------------------------------------------------------------------+
   //|   Remove all elements from the array                             |
   //+------------------------------------------------------------------+
   void Clear()
     {
      m_Size=0;
      m_ReservedSize = m_ChunkSize;
      ArrayResize(Element,m_ReservedSize);
     }

   //+------------------------------------------------------------------+
   //|   Function for getting the number of active elements in the array|
   //+------------------------------------------------------------------+
   int Size()
     {
      return(m_Size);
     }
  };
//+------------------------------------------------------------------+
