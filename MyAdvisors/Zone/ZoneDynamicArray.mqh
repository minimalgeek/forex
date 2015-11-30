//+------------------------------------------------------------------+
//|                                         MqlRatesDynamicArray.mqh |
//|                                                    Farago Balazs |
//|                                             https://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Farago Balazs"
#property link      "https://www.mql5.com"
#property version   "1.00"

#include "Zone.mqh"

class ZoneDynamicArray
{
private:
   int               m_ChunkSize;    // Chunk size
   int               m_ReservedSize; // Actual size of the array
   int               m_Size;         // Number of active elements in the array
   
   void quickSort(Zone& item[], int left, int right) {
      int    i, j;
      Zone   center, x;
      
      i=left;
      j=right;   
      center=item[(left+right)/2];
      while(i<=j) {
         while(item[i].strength < center.strength  && i < right) i++;
         while(item[j].strength > center.strength  && j > left)  j--;
         if(i <= j) {
            x        = item[i];
            item[i]  = item[j];
            item[j]  = x;
            i++; 
            j--;
         }
      } 
      if(left<j)  quickSort(item, left,   j);
      if(right>i) quickSort(item, i,      right);
  }
   
public:
   Zone              elements[];
   
   void ZoneDynamicArray(int ChunkSize=1024) {
      m_Size = 0;                            // Number of active elements
      m_ChunkSize = ChunkSize;               // Chunk size
      m_ReservedSize = ChunkSize;            // Actual size of the array
      ArrayResize(elements, m_ReservedSize); // Prepare the array
   }
   
   void addValue(Zone &value) {
      m_Size++; // Increase the number of active elements
      if(m_Size > m_ReservedSize) { // The required number is bigger than the actual array size
         m_ReservedSize += m_ChunkSize; // Calculate the new array size
         ArrayResize(elements, m_ReservedSize); // Increase the actual array size
      }
      elements[m_Size-1] = value; // Add the value
      sort();
   }
     
   Zone deleteLatest() {
      m_Size--;
      Zone latest = elements[0];
      ArrayCopy(elements,elements,0,1);
      sort();
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
   
   void sort() {
      if (m_Size > 0) {
         quickSort(elements, 0, m_Size - 1);
         
         /*
         Print("=== Strength order after sorting ===");
         for (int i = 0; i < m_Size; i++) {
            Print(elements[i].strength);
         }
         Print("===");
         */
      }
   }
};
