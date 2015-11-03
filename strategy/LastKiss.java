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

public class LastKiss implements IStrategy {
    
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    
    private IBar previousBar;
    private CrossObjectGroup resistanceCross;
    private CrossObjectGroup supportCross;
    private long minimumWaitingTime;
    private long waitingTime;
    
    private IOrder order;
    //2.define the following instance variables:
    private IChart openedChart;
    private IChartObjectFactory factory;
    private int signals;
    private int uniqueOrderCounter = 0;
    private double stopLossValue;
                    
    private List<IBar> resistanceBars;
    private List<IBar> supportBars;
      
    @Configurable(value="Instrument value")
    public Instrument myInstrument = Instrument.EURUSD;
    @Configurable(value="Offer Side value", obligatory=true)
    public OfferSide myOfferSide = OfferSide.BID;
    @Configurable(value="Period value")
    public Period myPeriod = Period.THIRTY_MINS;
    
    @Configurable("Bars to check")
    public int numberOfBars = 150;
    @Configurable("Adjacents")
    public int adjacents = 3;
    @Configurable("Maximum range in percentage")
    public double maxDifferencePercentage = 100.0;
    @Configurable("Range multiplier to identify new zones")
    public int multiplier = 4;
    @Configurable("Nr. of necessary turning points")
    public int turningPoints = 3;
    @Configurable("Max allowed size")
    public int maxAllowedSize = 5;
    @Configurable("Minimum wait for kissing candle")
    public int minCandlesToWait = 3;
    @Configurable("Maximum wait for kissing candle")
    public int candlesToWait = 10;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 40;
    
    
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
        supportBars = new LinkedList<IBar>();
        resistanceCross = null;
        supportCross = null;
        waitingTime = candlesToWait * myPeriod.getInterval();
        minimumWaitingTime = minCandlesToWait * myPeriod.getInterval();
        
        stopLossValue = myInstrument.getPipValue() * stopLossPips;

        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(myInstrument);                     
        context.setSubscribedInstruments(instruments, true);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        if(message.getOrder() != null) 
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message.getContent());
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
        
        calculateZones(resistanceBars, true);
        calculateZones(supportBars, false);
        
        drawPrices(resistanceBars, Color.RED);
        drawPrices(supportBars, Color.GREEN);
        
        if (resistanceCross == null && supportCross == null) {
            findCrossingBars();
        } else {
            waitForLastKiss();
        }
    }
    
    private void calculateZones(List<IBar> barList, boolean resistance) throws JFException {
        // we are not interested in the current bar, only in the completed ones
        
        List<IBar> localTurningPoints = new ArrayList<IBar>();
        for (int i = adjacents + 1; i <= numberOfBars; i++) {
            IBar bar = history.getBar(myInstrument, myPeriod, myOfferSide, i);
            
            boolean isTurningPoint = true;
            for (int j = - adjacents; j <= adjacents; j++) {
                if (j != 0) { // because it is the current bar what we check
                    IBar tempBar = history.getBar(myInstrument, myPeriod, myOfferSide, i + j);
                    if (resistance ? tempBar.getClose() > bar.getClose() : tempBar.getClose() < bar.getClose()) {
                        isTurningPoint = false;
                        break;
                    }
                }
            }
            
            if (isTurningPoint) {
                localTurningPoints.add(bar);
            }
        }
        
        for (IBar turningPoint : localTurningPoints) {
            
            int nrOfSimilarPoint = 0;
            
            for (IBar tempTurningPoint : localTurningPoints) {
                if (tempTurningPoint != turningPoint) {
                    double difference = Math.abs(tempTurningPoint.getClose() - turningPoint.getClose());
                    if (difference < getMaximumDifferenceForBar(turningPoint)) {
                        nrOfSimilarPoint++;
                    }
                }
            }
            
            if (nrOfSimilarPoint >= turningPoints && identifiableAsNewZone(turningPoint)) {
                addToPriceList(barList, turningPoint);
            }
        }
    }
    
    private boolean identifiableAsNewZone(IBar barToCheck) {
        for (IBar tempBar : resistanceBars) {
            double difference = Math.abs(tempBar.getClose() - barToCheck.getClose());
            if (difference < multiplier * getMaximumDifferenceForBar(barToCheck)) {
                return false;
            }
        }
        for (IBar tempBar : supportBars) {
            double difference = Math.abs(tempBar.getClose() - barToCheck.getClose());
            if (difference < multiplier * getMaximumDifferenceForBar(barToCheck)) {
                return false;
            }
        }
        return true;
    }
    
    private double getMaximumDifferenceForBar(IBar bar) {
        double diff = Math.abs(bar.getHigh() - bar.getLow());
        double maxDiff = (diff/100.0)*maxDifferencePercentage;
        return maxDiff;
    }
    
    private void addToPriceList(List<IBar> barList, IBar turningPoint) {
        if (barList.size() >= maxAllowedSize) {
            if (this.openedChart != null)
                openedChart.remove(createLabel(barList.get(0)));
            barList.remove(0);
        }
        
        barList.add(turningPoint);
    }   
    
    private void findCrossingBars() throws JFException {
        for (IBar bar : resistanceBars) {
            // bull candle
            if (isBull(previousBar) && crosses(bar, previousBar)) {
                printMe("Resistance crossing bar found: " + barDate(previousBar));
                resistanceCross = new CrossObjectGroup(previousBar, bar);
                drawUpOrDown("Res. cross ");
                return;
            }
        }
        
        for (IBar bar : supportBars) {
            // bear candle
            if (isBear(previousBar) && crosses(bar, previousBar)) {
                printMe("Support crossing bar found: " + barDate(previousBar));
                supportCross = new CrossObjectGroup(previousBar, bar);
                drawUpOrDown("Supp. cross ");
                return;
            }
        }
    }
    
    private void waitForLastKiss() throws JFException {
        if (resistanceCross != null) {
            if (previousBar.getTime() >= resistanceCross.crossingBar.getTime() + waitingTime) {
                printMe("Resistance signal is too old: " + barDate(resistanceCross.crossingBar));
                resistanceCross = null;
            } else if (isBear(previousBar) && shouldOpenTrade(resistanceCross)) {
                placeBuyStop();
                resistanceCross = null;
            }
        }
        
        if (supportCross != null) {
            if (previousBar.getTime() >= supportCross.crossingBar.getTime() + waitingTime) {
                printMe("Support signal is too old: " + barDate(supportCross.crossingBar));
                supportCross = null;
            } else if (isBull(previousBar) && shouldOpenTrade(supportCross)) {
                placeSellStop();
                supportCross = null;
            }
        }
    }
    
    private boolean shouldOpenTrade(CrossObjectGroup group){
        return crossesWithHL(group.crossedBar, previousBar) && previousBar.getTime() >= group.crossingBar.getTime() + minimumWaitingTime;
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
        drawUpOrDown("Buy stop ");
        IEngine.OrderCommand myCommand = IEngine.OrderCommand.BUYSTOP;

        double stopLossPrice = previousBar.getLow() - stopLossValue;
        double takeProfitPrice = previousBar.getHigh() + multiplier*getMaximumDifferenceForBar(previousBar);
        
        IOrder newOrder = engine.submitOrder("LastKissBuyStop" + uniqueOrderCounter++, myInstrument, myCommand, 0.1, previousBar.getHigh(), 1, stopLossPrice, takeProfitPrice, previousBar.getTime() + waitingTime);
    }
    
    private void placeSellStop() throws JFException {
        drawUpOrDown("Sell stop ");
        IEngine.OrderCommand myCommand = IEngine.OrderCommand.SELLSTOP;

        double stopLossPrice = previousBar.getHigh() + stopLossValue;
        double takeProfitPrice = previousBar.getLow() - multiplier*getMaximumDifferenceForBar(previousBar);
        
        IOrder newOrder = engine.submitOrder("LastKissSellStop" + uniqueOrderCounter++, myInstrument, myCommand, 0.1, previousBar.getLow(), 1, stopLossPrice, takeProfitPrice, previousBar.getTime() + waitingTime);
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
        
    private void drawPrices(List<IBar> barList, Color color) {
        if (this.openedChart == null) 
            return;
        
        for (IBar bar : barList) {
            String label = createLabel(bar);
            if (openedChart.get(label) == null) {
                IHorizontalLineChartObject hLine = factory.createHorizontalLine(label, bar.getClose());
                hLine.setColor(color);
                hLine.setText("started on: " + barDate(bar));
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
    
    private class CrossObjectGroup {
        public IBar crossingBar;
        public IBar crossedBar;
        
        public CrossObjectGroup() {}
        
        public CrossObjectGroup(IBar crossingBar, IBar crossedBar) {
            this.crossingBar = crossingBar;
            this.crossedBar = crossedBar;
        }
    }
    
}