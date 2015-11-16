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

public class LastKiss3 implements IStrategy {
    
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
    private long triggerWaitingTime;
    private double zoneBelly;
    
    private IOrder order;
    private IChart openedChart;
    private IChartObjectFactory factory;
    private int signals;
    private int uniqueOrderCounter = 0;
    
    private ZoneFinder zoneFinder;
      
    @Configurable(value="Instrument value")
    public Instrument myInstrument = Instrument.EURUSD;
    @Configurable(value="Offer Side value", obligatory=true)
    public OfferSide myOfferSide = OfferSide.BID;
    @Configurable(value="Period value")
    public Period myPeriod = Period.ONE_HOUR;
    
    @Configurable("Belly pips")
    public int bellyPips = 60;
    @Configurable("Belly based zone shrink")
    public double zoneShrink = 0.15;

    @Configurable("Candles to wait for kiss (min)")
    public int minCandlesToWait = 2;
    @Configurable("Candles to wait for kiss (max)")
    public int candlesToWait = 15;
    @Configurable("Candles to wait for trigger order")
    public int candlesToWaitOrder = 5;
    
    @Configurable("SL multiplier on belly")
    public double slMultiplier = 0.5;
    @Configurable("TP multiplier on belly")
    public double tpMultiplier = 1.1;
    
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

        zoneBelly = myInstrument.getPipValue()*bellyPips;
        printMe("ZoneBelly: " + zoneBelly);
        zoneFinder = new ZoneFinder(
                context, 
                myInstrument, 
                myOfferSide, 
                myPeriod, 
                zoneBelly, 
                zoneBelly * zoneShrink
            );
                        
        cog = null;
        waitingTime = candlesToWait * myPeriod.getInterval();
        minimumWaitingTime = minCandlesToWait * myPeriod.getInterval();
        triggerWaitingTime = candlesToWaitOrder * myPeriod.getInterval();
        
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
        zoneFinder.findZones();
        
        waitForLastKiss();
        findCrossingBars();
    }
    
    private void findCrossingBars() throws JFException {
        for (IBar resBar : zoneFinder.getZones()) {
            if (crosses(resBar)) {
                if (BarHelper.isBull(previousBar)) {
                    cog = new CrossObjectGroup(previousBar, resBar, Cross.RESISTANCE);
                    drawZoneCrossSignal();
                    return;
                }
                if (BarHelper.isBear(previousBar)) {
                    cog = new CrossObjectGroup(previousBar, resBar, Cross.SUPPORT);
                    drawZoneCrossSignal();
                    return;
                }
            }
        }
    }
    
    private void waitForLastKiss() throws JFException {
        if (cog != null) {
            if (previousBar.getTime() >= cog.crossingBar.getTime() + waitingTime) {
                printMe("Signal is too old: " + BarHelper.barDate(cog.crossingBar));
                cog = null;
            } else if (shouldOpenTrade()) {
                if (resistanceKissCandleAppeared()) {
                    placeBuyStop();
                    cog = null;
                } else if (supportKissCandleAppeared()) {
                    placeSellStop();
                    cog = null;
                }
            }
        }
    }
    
    private boolean shouldOpenTrade(){
        return previousBar.getTime() >= cog.crossingBar.getTime() + minimumWaitingTime;
    }
    
    private boolean resistanceKissCandleAppeared() {
        double crossedClose = cog.crossedBar.getClose();
        return cog.cross == Cross.RESISTANCE && 
               crossedClose < previousBar.getClose() && 
               crossedClose < previousBar.getOpen() && 
               crossedClose > previousBar.getLow(); // KISS
    }
    
    private boolean supportKissCandleAppeared() {
        double crossedClose = cog.crossedBar.getClose();
        return cog.cross == Cross.SUPPORT && 
               crossedClose > previousBar.getClose() && 
               crossedClose > previousBar.getOpen() && 
               crossedClose < previousBar.getHigh(); // KISS
    }
    
    private boolean crosses(IBar barWithClosePrice) {
        double closePrice = barWithClosePrice.getClose();
        
        return (previousBar.getOpen() < closePrice && previousBar.getClose() > closePrice) ||
               (previousBar.getClose() < closePrice && previousBar.getOpen() > closePrice);
    }
    
    // buy-sell functions
    
    private void placeBuyStop() throws JFException {
        if (order == null) {
            IEngine.OrderCommand myCommand = IEngine.OrderCommand.BUYSTOP;
    
            double high = previousBar.getHigh();
            double stopLossPrice = round(high - zoneBelly*slMultiplier, 4);
            double takeProfitPrice = round(high + zoneBelly*tpMultiplier, 4);
            drawStopOrder(high, stopLossPrice, takeProfitPrice);
    
            order = engine.submitOrder("LastKissBuyStop" + uniqueOrderCounter++, 
                myInstrument, 
                myCommand, 
                0.1, 
                high, 
                1, 
                stopLossPrice, 
                takeProfitPrice, 
                previousBar.getTime() + triggerWaitingTime);
        }
    }
    
    private void placeSellStop() throws JFException {
        if (order == null) {
            IEngine.OrderCommand myCommand = IEngine.OrderCommand.SELLSTOP;
    
            double low = previousBar.getLow();
            double stopLossPrice = round(low + zoneBelly*slMultiplier, 4);
            double takeProfitPrice = round(low - zoneBelly*tpMultiplier, 4);
            drawStopOrder(low, stopLossPrice, takeProfitPrice);
            
            order = engine.submitOrder("LastKissSellStop" + uniqueOrderCounter++, 
                myInstrument, 
                myCommand, 
                0.1, 
                low, 
                1, 
                stopLossPrice, 
                takeProfitPrice, 
                previousBar.getTime() + triggerWaitingTime);
        }
    }
    
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
    
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    
    // helper methods
    
    private void printMe(Object toPrint){
        console.getOut().println(toPrint);
    }
    
    private void printMeError(Object o){
        console.getErr().println(o);
    }
    
    // draw functions
        
    private void drawZoneCrossSignal() {
        if (this.openedChart == null)
            return;
            
        double space = myInstrument.getPipValue() * 2;
        IChartDependentChartObject signal = BarHelper.isBull(previousBar)
                ? factory.createSignalUp("signalUpKey" + signals++, previousBar.getTime(), previousBar.getLow() - space)
                : factory.createSignalDown("signalDownKey" + signals++, previousBar.getTime(), previousBar.getHigh() + space);
        signal.setText("Zone cross");
        openedChart.addToMainChart(signal);
    }
    
    private void drawStopOrder(double entry, double sl, double tp) {
        if (this.openedChart == null)
            return;
            
        boolean sell = tp < sl;
        
        IChartDependentChartObject signal = sell
                ? factory.createSignalDown("signalDownKey" + signals++, previousBar.getTime(), entry)
                : factory.createSignalUp("signalUpKey" + signals++, previousBar.getTime(), entry);
        signal.setText(sell ? "Sell stop" : "Buy stop");
        openedChart.addToMainChart(signal);
        
        IShortLineChartObject line = factory.createShortLine("stopOrderKey" + signals++, previousBar.getTime(), sl, previousBar.getTime(), tp);
        line.setColor(sell ? Color.RED : Color.GREEN);
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
    
    private static class BarHelper {
        public static boolean isBull(IBar bar) {
            return bar.getOpen() < bar.getClose();
        }
        
        public static boolean isBear(IBar bar) {
            return !isBull(bar);
        }
        
        public static String barDate(IBar bar) {
            return (new Date(bar.getTime())).toString();
        }
        
        public static String createUniqueLabel(IBar bar, String prefix) {
            return prefix + bar.getTime();
        }
    }
    
    private static class ZoneFinder {
    
        private static final String H_LINE = "hLine";
        private static final String CIRCLE = "circle";
        
        private static final String SMALL = "small";
        private static final String BIG = "big";

        private static final int numberOfBars = 300;
        private static final int adjacents = 4;
        private static final int turningPoints = 3;
        private static final int maxAllowedSize = 10;
                        
        private IEngine engine;
        private IConsole console;
        private IHistory history;
        private IContext context;
        private IUserInterface userInterface;
        
        private IChart openedChart;
        private IChartObjectFactory factory;
        
        public Instrument myInstrument;
        public OfferSide myOfferSide;
        public Period myPeriodForZones;
        
        private double zoneBelly;
        private double shrinkedZoneBelly;
        
        private List<IBar> resistanceBars;
        
        public ZoneFinder(IContext context, 
                               Instrument myInstrument,
                               OfferSide myOfferSide,
                               Period myPeriodForZones,
                               double zoneBelly,
                               double shrinkedZoneBelly) throws JFException {
            
            this.context = context;
            
            this.myInstrument = myInstrument;
            this.myOfferSide = myOfferSide;
            this.myPeriodForZones = myPeriodForZones;
            
            this.zoneBelly = zoneBelly;
            this.shrinkedZoneBelly = shrinkedZoneBelly;                        
                                   
            this.engine = context.getEngine();
            this.console = context.getConsole();
            this.history = context.getHistory();
            this.userInterface = context.getUserInterface();
            
            this.openedChart = context.getChart(this.myInstrument);
            if (this.openedChart != null)
                this.factory = openedChart.getChartObjectFactory();
            
            this.resistanceBars = new LinkedList<IBar>();
        }

        public List<IBar> getZones() {
            return this.resistanceBars;
        }
        
        public void findZones() throws JFException {
            List<IBar> localTurningPoints = findTurningPoints();
            
            for (IBar turningPoint : localTurningPoints) {
                int nrOfSimilarPoint = 0;
                
                for (IBar tempTurningPoint : localTurningPoints) {
                    if (tempTurningPoint != turningPoint) {
                        double difference = Math.abs(tempTurningPoint.getClose() - turningPoint.getClose());
                        if (difference < shrinkedZoneBelly) {
                            nrOfSimilarPoint++;
                        }
                    }
                }
                
                if (nrOfSimilarPoint >= turningPoints && identifiableAsNewZone(turningPoint)) {
                    addToPriceList(turningPoint);
                }
            }
            
            drawPrices();
        }
        
        private List<IBar> findTurningPoints() throws JFException {
            List<IBar> retList = new ArrayList<IBar>();
            for (int i = adjacents + 1; i <= numberOfBars; i++) { // we are not interested in the current bar, only in the completed ones
                IBar bar = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i);
                double closePrice = bar.getClose();
    
                boolean smallerTurningPoint = true, biggerTurningPoint = true;
                
                for (int j = 1; j <= adjacents; j++) {
                    IBar tempBarPrev = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i - j);
                    IBar tempBarNext = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i + j);
                    
                    if (tempBarPrev.getClose() < closePrice || tempBarNext.getClose() < closePrice) {
                        smallerTurningPoint = false;
                    }
                    if (tempBarPrev.getClose() > closePrice || tempBarNext.getClose() > closePrice) {
                        biggerTurningPoint = false;
                    }
                }
                
                if (smallerTurningPoint || biggerTurningPoint) {
                    retList.add(bar);
                    drawTurningPointSignal(bar);
                }
            }
            
            return retList;
        }
        
        private boolean identifiableAsNewZone(IBar barToCheck) {
            for (IBar tempBar : resistanceBars) {
                double difference = Math.abs(tempBar.getClose() - barToCheck.getClose());
                if (difference < zoneBelly) {
                    return false;
                }
            }
            return true;
        }
        
        private void addToPriceList(IBar turningPoint) {
            if (resistanceBars.size() >= 10) {
                if (this.openedChart != null) {
                    String firstBarLabel = BarHelper.createUniqueLabel(resistanceBars.get(0), H_LINE);
                    openedChart.remove(firstBarLabel);
                    openedChart.remove(firstBarLabel + SMALL);
                    openedChart.remove(firstBarLabel + BIG);
                }
                resistanceBars.remove(0);
            }
            
            resistanceBars.add(turningPoint);
        }
        
        private void drawTurningPointSignal(IBar bar) throws JFException {
            String label = BarHelper.createUniqueLabel(bar, CIRCLE);
            if (this.openedChart == null || this.openedChart.get(label) != null)
                return;
                
            double space = myInstrument.getPipValue() * 2;
            IChartDependentChartObject signal = factory.createSignalUp(label, bar.getTime(), bar.getClose());
            signal.setText("TP");
            signal.setColor(Color.GRAY);
            openedChart.addToMainChart(signal);
        }
        
         private void drawPrices() {
            if (this.openedChart == null) 
                return;
            
            for (IBar bar : resistanceBars) {
                String label = BarHelper.createUniqueLabel(bar, H_LINE);
                if (openedChart.get(label) == null) {
                    IHorizontalLineChartObject hLine = factory.createHorizontalLine(label, bar.getClose());
                    hLine.setColor(Color.RED);
                    hLine.setText(BarHelper.barDate(bar));
                    openedChart.add(hLine);
                    
                    IHorizontalLineChartObject hLineSmall = factory.createHorizontalLine(label + SMALL, bar.getClose() - shrinkedZoneBelly);
                    hLineSmall.setColor(Color.BLUE);
                    hLineSmall.setLineStyle(LineStyle.DOT);
                    openedChart.add(hLineSmall);
                    
                    IHorizontalLineChartObject hLineBig = factory.createHorizontalLine(label + BIG, bar.getClose() + shrinkedZoneBelly);
                    hLineBig.setColor(Color.BLUE);
                    hLineBig.setLineStyle(LineStyle.DOT);
                    openedChart.add(hLineBig);
                }
            }
        }
    }
    
}