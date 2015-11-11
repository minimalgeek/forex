package jforex;

import java.util.*;

import com.dukascopy.api.*;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.drawings.IChartObjectFactory;
import com.dukascopy.api.drawings.IHorizontalLineChartObject;
import java.awt.Color;

public class WammieMoolah implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    
    private IBar previousBar;
    private IOrder order;
    private IChart openedChart;
    private IChartObjectFactory factory;

    private int signals;
    private int uniqueOrderCounter = 0;
    private long triggerWaitingTime;
    
    private ZoneFinder zoneFinder;
    
    @Configurable(value="Instrument value")
    public Instrument myInstrument = Instrument.EURUSD;
    @Configurable(value="Offer Side value", obligatory=true)
    public OfferSide myOfferSide = OfferSide.BID;
    @Configurable(value="Period value")
    public Period myPeriod = Period.ONE_HOUR;
    @Configurable(value="Period to search for zones")
    public Period myPeriodForZones = Period.ONE_HOUR;
    
    @Configurable("Belly pips")
    public int bellyPips = 50;
    @Configurable("Belly based zone shrink")
    public double zoneShrink = 0.15;

    @Configurable("Bars to check for zones")
    public int numberOfBars = 300;
    @Configurable("Nr. of adjacents on check close price")
    public int adjacents = 3;
    @Configurable("Nr. of necessary turning points")
    public int turningPoints = 4;
    @Configurable("Zone queue length")
    public int maxAllowedSize = 10;
    @Configurable("Candles to wait for trigger order")
    public int candlesToWaitOrder = 5;
    
    @Configurable("SL multiplier on belly")
    public double slMultiplier = 0.5;
    @Configurable("TP multiplier on belly")
    public double tpMultiplier = 1.0;
    
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
        
        double zoneBelly = myInstrument.getPipValue()*bellyPips;        
        zoneFinder = new WammieMoolah.ZoneFinder(
                context, 
                turningPoints, 
                adjacents, 
                numberOfBars, 
                myInstrument, 
                myOfferSide, 
                myPeriodForZones, 
                zoneBelly, 
                zoneBelly * zoneShrink
            );
        
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
    
    // draw functions
    
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
    
    private static class ZoneFinder {
    
        private static final String H_LINE = "hLine";
        private static final String CIRCLE = "circle";
        
        private static final String SMALL = "small";
        private static final String BIG = "big";
        
        private IEngine engine;
        private IConsole console;
        private IHistory history;
        private IContext context;
        private IUserInterface userInterface;
        
        private IChart openedChart;
        private IChartObjectFactory factory;
        
        private int turningPoints;
        private int adjacents;
        private int numberOfBars;
        
        public Instrument myInstrument;
        public OfferSide myOfferSide;
        public Period myPeriodForZones;
        
        private double zoneBelly;
        private double shrinkedZoneBelly;
        
        private List<IBar> resistanceBars;
        
        public ZoneFinder(IContext context, 
                               int turningPoints, 
                               int adjacents,
                               int numberOfBars,
                               Instrument myInstrument,
                               OfferSide myOfferSide,
                               Period myPeriodForZones,
                               double zoneBelly,
                               double shrinkedZoneBelly) throws JFException {
            
            this.myInstrument = myInstrument;
            this.myOfferSide = myOfferSide;
            this.myPeriodForZones = myPeriodForZones;                               
                                   
            this.engine = context.getEngine();
            this.console = context.getConsole();
            this.history = context.getHistory();
            this.context = context;
            this.userInterface = context.getUserInterface();
            
            this.openedChart = context.getChart(this.myInstrument);
            if (this.openedChart != null)
                this.factory = openedChart.getChartObjectFactory();
            
            this.resistanceBars = new LinkedList<IBar>();
            this.turningPoints = turningPoints;
            this.adjacents = adjacents;
            this.numberOfBars = numberOfBars;
            
            this.zoneBelly = zoneBelly;
            this.shrinkedZoneBelly = shrinkedZoneBelly;
        }
        
        
        
        public void setZoneBelly(double zoneBelly) {
            this.zoneBelly = zoneBelly;
        }
        
        public void setShrinkedZoneBelly(double shrinkedZoneBelly) {
            this.shrinkedZoneBelly = shrinkedZoneBelly;
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
    
                boolean smallerTurningPoint = true, biggerTurningPoint = true;
                
                for (int j = 1; j <= adjacents; j++) {
                    IBar tempBarPrev = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i - j);
                    IBar tempBarNext = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i + j);
                    
                    if (tempBarPrev.getClose() < bar.getClose() && tempBarNext.getClose() < bar.getClose()) {
                        continue;
                    }
                    smallerTurningPoint = false;
                    break;
                }
                if (!smallerTurningPoint) {
                    for (int j = 1; j <= adjacents; j++) {
                        IBar tempBarPrev = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i - j);
                        IBar tempBarNext = history.getBar(myInstrument, myPeriodForZones, myOfferSide, i + j);
                        
                        if (tempBarPrev.getClose() > bar.getClose() && tempBarNext.getClose() > bar.getClose()) {
                            continue;
                        }
                        biggerTurningPoint = false;
                        break;
                    }
                }
                
                if (smallerTurningPoint || biggerTurningPoint) {
                    retList.add(bar);
                    drawCircleOn(bar);
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
                    String firstBarLabel = createLabel(resistanceBars.get(0), H_LINE);
                    openedChart.remove(firstBarLabel);
                    openedChart.remove(firstBarLabel + SMALL);
                    openedChart.remove(firstBarLabel + BIG);
                }
                resistanceBars.remove(0);
            }
            
            resistanceBars.add(turningPoint);
        }
        
        private void drawCircleOn(IBar bar) throws JFException {
            String label = createLabel(bar, CIRCLE);
            if (this.openedChart == null || this.openedChart.get(label) != null)
                return;
                
            
            long spaceHorizontal = myPeriodForZones.getInterval();
            double spaceVertical = myInstrument.getPipValue() * 5;
            IEllipseChartObject ellipse = factory.createEllipse(label, 
                bar.getTime() - spaceHorizontal, 
                bar.getClose() - spaceVertical, 
                bar.getTime() + spaceHorizontal, 
                bar.getClose() + spaceVertical);
            ellipse.setLineWidth(1f);
            ellipse.setColor(Color.GREEN);
            ellipse.setFillColor(Color.YELLOW);
            openedChart.addToMainChart(ellipse);
        }
        
         private void drawPrices() {
            if (this.openedChart == null) 
                return;
            
            for (IBar bar : resistanceBars) {
                String label = createLabel(bar, H_LINE);
                if (openedChart.get(label) == null) {
                    IHorizontalLineChartObject hLine = factory.createHorizontalLine(label, bar.getClose());
                    hLine.setColor(Color.RED);
                    hLine.setText(barDate(bar));
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
        
        private String createLabel(IBar bar, String prefix) {
            return prefix + bar.getTime();
        }
        
        private String barDate(IBar bar) {
            return (new Date(bar.getTime())).toString();
        }
    }
}
