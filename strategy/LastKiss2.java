package jforex;

import com.dukascopy.api.*;
import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.IUserInterface;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.drawings.IChartObjectFactory;
import com.dukascopy.api.drawings.IHorizontalLineChartObject;
import java.awt.Color;
import java.util.*;

public class LastKiss2 implements IStrategy {
    
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    
    private IBar previousBar;
    private CrossObjectGroup cog;
    private long minimumWaitingTime;
    private long waitingTime;
    private double zoneBelly;
    
    private IOrder order;
    private IChart openedChart;
    private IChartObjectFactory factory;
    private int signals;
    private int uniqueOrderCounter = 0;
                    
    private List<IBar> resistanceBars;
      
    @Configurable(value="Instrument value")
    public Instrument myInstrument = Instrument.EURUSD;
    @Configurable(value="Offer Side value", obligatory=true)
    public OfferSide myOfferSide = OfferSide.BID;
    @Configurable(value="Period value")
    public Period myPeriod = Period.THIRTY_MINS;
    
    @Configurable("Bars to check for belly")
    public int bellyBars = 70;
    @Configurable("Range multiplier to identify new zones")
    public int multiplier = 5;
    @Configurable("Belly based zone shrink")
    public double zoneShrink = 0.9;
    
    // Checked parameters
    @Configurable("Bars to check for zones")
    public int numberOfBars = 200;
    @Configurable("Nr. of adjacents on check close price")
    public int adjacents = 3;
    @Configurable("Nr. of necessary turning points")
    public int turningPoints = 3;
    @Configurable("Zone queue length")
    public int maxAllowedSize = 10;
    @Configurable("Candles to wait for kiss (min)")
    public int minCandlesToWait = 3;
    @Configurable("Candles to wait for kiss (max)")
    public int candlesToWait = 14;
    @Configurable("TP multiplier on belly")
    public double tpMultiplier = 1.0;
    @Configurable("SL multiplier on belly")
    public double slMultiplier = 0.5;
    
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        
        this.openedChart = context.getChart(myInstrument);
        if (this.openedChart != null)
            this.factory = openedChart.getChartObjectFactory();
        
        resistanceBars = new LinkedList<IBar>();
        cog = null;
        
        waitingTime = candlesToWait * myPeriod.getInterval();
        minimumWaitingTime = minCandlesToWait * myPeriod.getInterval();
        
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(myInstrument);                     
        context.setSubscribedInstruments(instruments, true);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        if(message.getOrder() != null) {
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message.getContent());
            IOrder.State state = message.getOrder().getState();
            if (state == IOrder.State.CLOSED || state == IOrder.State.CANCELED) {
                printMe("============================");
                order = null;
            }
        }
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        
        if (!instrument.equals(myInstrument) || !period.equals(myPeriod)) {
            return; //quit
        }                 

        previousBar = history.getBar(myInstrument, myPeriod, myOfferSide, 1);
        zoneBelly = getMaximumDifferenceForBar();
        
        calculateZones();
        drawPrices();
        
        if (cog != null) {
            waitForLastKiss();
        }
        findCrossingBars();
    }
    
    private double getMaximumDifferenceForBar() throws JFException {
        List<IBar> bars = new ArrayList<IBar>();
        for (int i = 1; i <= bellyBars; i++) { // we are not interested in the current bar, only in the completed ones
            bars.add(history.getBar(myInstrument, myPeriod, myOfferSide, i));
        }
        
        Statistics stat = new Statistics(bars);
        return stat.getStdDev();
    }
    
    private void calculateZones() throws JFException {
        List<IBar> localTurningPoints = findTurningPoints();
        
        for (IBar turningPoint : localTurningPoints) {
            int nrOfSimilarPoint = 0;
            
            for (IBar tempTurningPoint : localTurningPoints) {
                if (tempTurningPoint != turningPoint) {
                    double difference = Math.abs(tempTurningPoint.getClose() - turningPoint.getClose());
                    if (difference < zoneShrink*zoneBelly) {
                        nrOfSimilarPoint++;
                    }
                }
            }
            
            if (nrOfSimilarPoint >= turningPoints && identifiableAsNewZone(turningPoint)) {
                addToPriceList(turningPoint);
            }
        }
    }
    
    private List<IBar> findTurningPoints() throws JFException {
        List<IBar> retList = new ArrayList<IBar>();
        for (int i = adjacents + 1; i <= numberOfBars; i++) { // we are not interested in the current bar, only in the completed ones
            IBar bar = history.getBar(myInstrument, myPeriod, myOfferSide, i);
            
            boolean isTurningPoint = true;
            Boolean previous = null;
            for (int j = - adjacents; j <= adjacents; j++) {
                if (j != 0) { // because it is the current bar what we check
                    IBar tempBar = history.getBar(myInstrument, myPeriod, myOfferSide, i + j);
                    if (previous != null && (tempBar.getClose() > bar.getClose()) != previous) {
                        isTurningPoint = false;
                        break;
                    }
                    previous = tempBar.getClose() > bar.getClose();
                }
            }
            
            if (isTurningPoint) {
                retList.add(bar);
            }
        }
        
        return retList;
    }
    
    private boolean identifiableAsNewZone(IBar barToCheck) {
        for (IBar tempBar : resistanceBars) {
            double difference = Math.abs(tempBar.getClose() - barToCheck.getClose());
            if (difference < multiplier * zoneBelly) {
                return false;
            }
        }
        return true;
    }
    
    private void addToPriceList(IBar turningPoint) {
        if (resistanceBars.size() >= maxAllowedSize) {
            if (this.openedChart != null)
                openedChart.remove(createLabel(resistanceBars.get(0)));
            resistanceBars.remove(0);
        }
        
        resistanceBars.add(turningPoint);
    }   
    
    private void findCrossingBars() throws JFException {
        for (IBar resBar : resistanceBars) {
            if (isBull(previousBar) && crosses(resBar, previousBar)) {
                cog = new CrossObjectGroup(previousBar, resBar, Cross.RESISTANCE);
                drawUpOrDown("Res. cross ");
                return;
            }
            if (isBear(previousBar) && crosses(resBar, previousBar)) {
                cog = new CrossObjectGroup(previousBar, resBar, Cross.SUPPORT);
                drawUpOrDown("Supp. cross ");
                return;
            }
        }
    }
    
    private void waitForLastKiss() throws JFException {
        if (cog != null) {
            if (previousBar.getTime() >= cog.crossingBar.getTime() + waitingTime) {
                printMe("Signal is too old: " + barDate(cog.crossingBar));
                cog = null;
            } else if (shouldOpenTrade()) {
                if (cog.cross == Cross.RESISTANCE && isBear(previousBar)) {
                    placeBuyStop();
                    cog = null;
                } else if (cog.cross == Cross.SUPPORT && isBull(previousBar)) {
                    placeSellStop();
                    cog = null;
                }
            }
        }
    }
    
    private boolean shouldOpenTrade(){
        // belelóg és eltelt már a minimum idő
        return crossesWithHL(cog.crossedBar, previousBar) && previousBar.getTime() >= cog.crossingBar.getTime() + minimumWaitingTime;
    }
    
    private boolean crosses(IBar barWithClosePrice, IBar barToCheck) {
        double closePrice = barWithClosePrice.getClose();
        
        return (barToCheck.getOpen() < closePrice && barToCheck.getClose() > closePrice) ||
               (barToCheck.getClose() < closePrice && barToCheck.getOpen() > closePrice);
    }
    
    private boolean crossesWithHL(IBar barWithClosePrice, IBar barToCheck) {
        double closePrice = barWithClosePrice.getClose();
        return barToCheck.getLow() < closePrice && barToCheck.getHigh() > closePrice;
    }
    
    // buy-sell functions
    
    private void placeBuyStop() throws JFException {
        if (order == null) {
            IEngine.OrderCommand myCommand = IEngine.OrderCommand.BUYSTOP;
    
            double high = previousBar.getHigh();
            double stopLossPrice = round(high - slMultiplier*zoneBelly, 4);
            double takeProfitPrice = round(high + tpMultiplier*zoneBelly, 4);
            drawStopOrder(high, stopLossPrice, takeProfitPrice);
    
            order = engine.submitOrder("LastKissBuyStop" + uniqueOrderCounter++, 
                myInstrument, 
                myCommand, 
                0.1, 
                high, 
                1, 
                stopLossPrice, 
                takeProfitPrice, 
                previousBar.getTime() + waitingTime/2);
        }
    }
    
    private void placeSellStop() throws JFException {
        if (order == null) {
            IEngine.OrderCommand myCommand = IEngine.OrderCommand.SELLSTOP;
    
            double low = previousBar.getLow();
            double stopLossPrice = round(low  + slMultiplier*zoneBelly, 4);
            double takeProfitPrice = round(low - tpMultiplier*zoneBelly, 4);
            drawStopOrder(low, stopLossPrice, takeProfitPrice);
            
            order = engine.submitOrder("LastKissSellStop" + uniqueOrderCounter++, 
                myInstrument, 
                myCommand, 
                0.1, 
                low, 
                1, 
                stopLossPrice, 
                takeProfitPrice, 
                previousBar.getTime() + waitingTime/2);
        }
    }
    
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
    
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    // bar helper functions
        
    private boolean isBull(IBar bar) {
        return bar.getOpen() < bar.getClose();
    }
    
    private boolean isBear(IBar bar) {
        return !isBull(bar);
    }
    
    // helper methods
    
    private void printMe(Object toPrint){
        console.getOut().println(toPrint);
    }
    
    private void printMeError(Object o){
        console.getErr().println(o);
    }
    
    private String createLabel(IBar bar) {
        return "hLine_" + bar.getTime();
    }
    
    private String barDate(IBar bar) {
        return (new Date(bar.getTime())).toString();
    }
    
    // draw functions
        
    private void drawPrices() {
        if (this.openedChart == null) 
            return;
        
        for (IBar bar : resistanceBars) {
            String label = createLabel(bar);
            if (openedChart.get(label) == null) {
                IHorizontalLineChartObject hLine = factory.createHorizontalLine(label, bar.getClose());
                hLine.setColor(Color.RED);
                hLine.setText(barDate(bar));
                openedChart.add(hLine);
            }
        }
    }
    
    private void drawUpOrDown(String name) {
        if (this.openedChart == null)
            return;
            
        double space = myInstrument.getPipValue() * 2;
        IChartDependentChartObject signal = isBull(previousBar)
                ? factory.createSignalUp("signalUpKey" + signals++, previousBar.getTime(), previousBar.getLow() - space)
                : factory.createSignalDown("signalDownKey" + signals++, previousBar.getTime(), previousBar.getHigh() + space);
        signal.setText(name + (uniqueOrderCounter - 1));
        openedChart.addToMainChart(signal);
    }
    
    private void drawStopOrder(double entry, double sl, double tp) {
        if (this.openedChart == null)
            return;
            
        boolean up = isBull(previousBar);
        
        double space = myInstrument.getPipValue() * 2;
        IChartDependentChartObject signal = up
                ? factory.createSignalUp("signalUpKey" + signals++, previousBar.getTime(), previousBar.getLow() - space)
                : factory.createSignalDown("signalDownKey" + signals++, previousBar.getTime(), previousBar.getHigh() + space);
        signal.setText(up ? "Sell stop on " : "Buy stop on " + entry);
        openedChart.addToMainChart(signal);
        
        IShortLineChartObject line = factory.createShortLine("stopOrderKey" + signals++, previousBar.getTime(), sl, previousBar.getTime(), tp);
        line.setColor(up ? Color.RED : Color.GREEN);
        openedChart.addToMainChart(line);
    }
    
    private enum Cross {
        RESISTANCE, SUPPORT;
    }
    
    private class CrossObjectGroup {
        public IBar crossingBar;
        public IBar crossedBar;
        public Cross cross;
        
        public CrossObjectGroup() {}
        
        public CrossObjectGroup(IBar crossingBar, IBar crossedBar, Cross cross) {
            this.crossingBar = crossingBar;
            this.crossedBar = crossedBar;
            this.cross = cross;
        }
    }
    
    private class Statistics 
    {
        List<IBar> data;
    
        public Statistics(List<IBar> data) 
        {
            this.data = data;
        }   
    
        double getMean()
        {
            double sum = 0.0;
            for(IBar a : data)
                sum += a.getClose();
            return sum/data.size();
        }
    
        double getVariance()
        {
            double mean = getMean();
            double temp = 0;
            for(IBar a : data)
                temp += (mean - a.getClose())*(mean - a.getClose());
            return temp/data.size();
        }
    
        double getStdDev()
        {
            return Math.sqrt(getVariance());
        }
    }
}