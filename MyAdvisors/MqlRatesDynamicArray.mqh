//+------------------------------------------------------------------+
//|                                         MqlRatesDynamicArray.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

class MqlRatesDynamicArray
  {
private:
   int               m_ChunkSize;    // Chunk size
   int               m_ReservedSize; // Actual size of the array
   int               m_Size;         // Number of active elements in the array
public:
   MqlRates          elements[];
   
   void MqlRatesDynamicArray(int ChunkSize=1024) {
      m_Size=0;                            // Number of active elements
      m_ChunkSize=ChunkSize;               // Chunk size
      m_ReservedSize=ChunkSize;            // Actual size of the array
      ArrayResize(elements,m_ReservedSize); // Prepare the array
   }
   
   void addValue(MqlRates &Value) {
      m_Size++;
      if(m_Size>m_ReservedSize) {
         m_ReservedSize+=m_ChunkSize;
         ArrayResize(elements,m_ReservedSize);
      }
      elements[m_Size-1]=Value; // Add the value
   }
     
   MqlRates deleteLatest() {
      m_Size--;
      MqlRates latest = elements[0];
      ArrayCopy(elements,elements,0,1);
      return(latest);
   }

   void clear() {
      m_Size=0;
      m_ReservedSize = m_ChunkSize;
      ArrayResize(elements,m_ReservedSize);
   }

   int size() {
      return(m_Size);
   }
};
