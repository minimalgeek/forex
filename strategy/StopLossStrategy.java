package stoploss;

import com.dukascopy.api.*;
import java.util.HashSet;
import java.util.Set;
import com.dukascopy.api.drawings.IChartObjectFactory;
import com.dukascopy.api.drawings.IOhlcChartObject;
import com.dukascopy.api.indicators.OutputParameterInfo.DrawingStyle;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
//1. Add following imports:
import com.dukascopy.api.drawings.IChartDependentChartObject;
import com.dukascopy.api.drawings.ITriangleChartObject;
import com.dukascopy.api.drawings.ITextChartObject;
//2. Remove unneccesary imports
//import java.awt.Font;



public class StopLossStrategy implements IStrategy {

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
    private static final int PREV = 1;
    private static final int SECOND_TO_LAST = 0;
    //3. Define new instance variables:
    private int uniqueOrderCounter = 1;
    private SMATrend previousSMADirection = SMATrend.NOT_SET;
    private SMATrend currentSMADirection = SMATrend.NOT_SET;
    private Map<IOrder, Boolean> createdOrderMap = new HashMap<IOrder, Boolean>();
    private int shorLineCounter;
    private int textCounterOldSL;
    private int textCounterNewSL;
    
    @Configurable(value = "Instrument value")
    public Instrument myInstrument = Instrument.EURUSD;
    @Configurable(value = "Offer Side value", obligatory = true)
    public OfferSide myOfferSide;
    @Configurable(value = "Period value")
    public Period myPeriod = Period.TEN_MINS;
    @Configurable("SMA time period")
    public int smaTimePeriod = 30;
    @Configurable("Add OHLC Index to chart")
    public boolean addOHLC = true;
    @Configurable("Filter")
    public Filter filter = Filter.WEEKENDS;
    @Configurable("Draw SMA")
    public boolean drawSMA = true;
    @Configurable("Close chart on onStop")
    public boolean closeChart;
    //4. Add new parameters
    @Configurable("Stop loss in pips")
    public int stopLossPips = 10;
    @Configurable("Take profit in pips")
    public int takeProfitPips = 10;
    @Configurable("Break event pips")
    public double breakEventPips = 5;
    
    //5. Define new enum which will hold the possible values of SMA trend line
    private enum SMATrend {
        UP, DOWN, NOT_SET;
    }

    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        this.openedChart = context.getChart(myInstrument);
        this.factory = openedChart.getChartObjectFactory();

        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(myInstrument);
        context.setSubscribedInstruments(instruments, true);

        if (drawSMA) {
            if (!addToChart(openedChart)) {
                printMeError("Indicators did not get plotted on chart. Check the chart values!");
            }
        }
    }//end of onStart method

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        if (message.getOrder() != null) {
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message);
        }
    }

    public void onStop() throws JFException {}

    /*
     * 6. Implement the onTick method. onTick method is called every time a tick happens. 
     * We are filtering out the our interesting instruments only, so the method is executed 
     * only when we need to. Later in the onBar method we add every new order to a Map object. 
     * Here we check this Map for all orders to see if a SL price is already changed for an order. 
     * If it is not changed, then we check if the profit in pips is greater than breakEventPips parameter. 
     * If it is, then we can change the SL value to order's open price level. Every time the SL value 
     * is set to open price, we add a triangle (by invoking a addBreakToChart method) to indicate 
     * the process visually on the chart. Finally we change the order's SL price and update the entry in the Map. 
     */
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != myInstrument) {
            return;
        }
        for (Map.Entry<IOrder, Boolean> entry : createdOrderMap.entrySet()) {
            IOrder currentOrder = entry.getKey();
            boolean currentValue = entry.getValue();
            if (currentValue == false && currentOrder.getProfitLossInPips() >= breakEventPips) {
                printMe("Order has profit of " + currentOrder.getProfitLossInPips() + " pips! Moving the stop loss to the open price.");
                addBreakToChart(currentOrder, tick, currentOrder.getStopLossPrice(), currentOrder.getOpenPrice());
                //add a line to the chart indicating the SL changes
                currentOrder.setStopLossPrice(currentOrder.getOpenPrice());
                entry.setValue(true);
            }
        }
    }//end of onTick method

    
    /*
     * 7. We are changing the onBar method in the way that it will use the SMATrend enum values 
     * to check when to create a new order. We are setting a SL and TP values, too. One difference 
     * from previous example is that we're not closing the previous order if a new one is opened. 
     * The orders are closed automatically when the SL or TP values are reached. 
     * Plus, all new orders are saved in the Map for later checking in the onTick method.
     */
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!instrument.equals(myInstrument) || !period.equals(myPeriod)) {
            return; //quit
        }
    
        int candlesBefore = 2, candlesAfter = 0;
        long completedBarTimeL = myOfferSide == OfferSide.ASK ? askBar.getTime() : bidBar.getTime();
        double sma[] = indicators.sma(instrument, period, myOfferSide, IIndicators.AppliedPrice.CLOSE,
                smaTimePeriod, Filter.NO_FILTER, candlesBefore, completedBarTimeL, candlesAfter);
        printMe(String.format("Bar SMA Values: Second-to-last = %.5f; Last Completed = %.5f", sma[SECOND_TO_LAST], sma[PREV]));
    
        IEngine.OrderCommand myCommand = null;
        printMe(String.format("Bar SMA Values: Second-to-last = %.5f; Last Completed = %.5f", sma[SECOND_TO_LAST], sma[PREV]));
        if (sma[PREV] > sma[SECOND_TO_LAST]) {
            printMe("SMA in up-trend"); //indicator goes up
            myCommand = IEngine.OrderCommand.BUY;
            currentSMADirection = SMATrend.UP;
        } else if (sma[PREV] < sma[SECOND_TO_LAST]) {
            printMe("SMA in down-trend"); //indicator goes down
            myCommand = IEngine.OrderCommand.SELL;
            currentSMADirection = SMATrend.DOWN;
        } else {
            return;
        }
    
        double lastTickBid = history.getLastTick(myInstrument).getBid();
        double lastTickAsk = history.getLastTick(myInstrument).getAsk();
        double stopLossValueForLong = myInstrument.getPipValue() * stopLossPips;
        double stopLossValueForShort = myInstrument.getPipValue() * takeProfitPips;
        double stopLossPrice = myCommand.isLong() ? (lastTickBid - stopLossValueForLong) : (lastTickAsk + stopLossValueForLong);
        double takeProfitPrice = myCommand.isLong() ? (lastTickBid + stopLossValueForShort) : (lastTickAsk - stopLossValueForShort);
    
        //if SMA trend direction is changed, then create a new order
        if (currentSMADirection != previousSMADirection) {
            previousSMADirection = currentSMADirection;
            IOrder newOrder = engine.submitOrder("MyStrategyOrder" + uniqueOrderCounter++, instrument, myCommand, 0.1, 0, 1, stopLossPrice, takeProfitPrice);
            createdOrderMap.put(newOrder, false);
    
            if(openedChart == null){
                return;
            }
            long time = bidBar.getTime() + myPeriod.getInterval(); //draw the  ISignalDownChartObject in the current bar
            double space = myInstrument.getPipValue() * 2; //space up or down from bar for ISignalDownChartObject
            IChartDependentChartObject signal = myCommand.isLong()
                    ? factory.createSignalUp("signalUpKey" + signals++, time, bidBar.getLow() - space)
                    : factory.createSignalDown("signalDownKey" + signals++, time, bidBar.getHigh() + space);
            signal.setStickToCandleTimeEnabled(false);
            signal.setText("MyStrategyOrder" + (uniqueOrderCounter - 1));
            openedChart.addToMainChart(signal);
        }
    
    }//end of onBar method

    private void printMe(Object toPrint) {
        console.getOut().println(toPrint);
    }
    

    private void printMeError(Object o) {
        console.getErr().println(o);
    }

    
    /*
     * 8. We are modifying the addToChart method so that the chart-checking 
     * happens in a new method - checkChart. 
     */
private boolean addToChart(IChart chart) {
    if (!checkChart(chart)) {
        return false;
    }

    chart.addIndicator(indicators.getIndicator("SMA"), new Object[]{smaTimePeriod},
            new Color[]{Color.BLUE}, new DrawingStyle[]{DrawingStyle.LINE}, new int[]{3});

    if (addOHLC) {
        IOhlcChartObject ohlc = null;
        for (IChartObject obj : chart.getAll()) {
            if (obj instanceof IOhlcChartObject) {
                ohlc = (IOhlcChartObject) obj;
            }
        }
        if (ohlc == null) {
            ohlc = chart.getChartObjectFactory().createOhlcInformer();
            ohlc.setPreferredSize(new Dimension(100, 200));
            chart.addToMainChart(ohlc);
        }
        //show the ohlc index
        ohlc.setShowIndicatorInfo(true);
    }
    return true;
}//end of addToChart method

    
    /*
     * 9. This is the method where we are adding a triangles to our chart if the SL value is changed
     * for an order. Green triangle represents the SL changes for long orders, red one for shord orders. 
     * The triangle is drawn so that it starts when the order is created and ends when the SL value
     * is changed. We add a text to the triangle to represent the old and new values of the SL. 
     */    
    private void addBreakToChart(IOrder changedOrder, ITick tick, double oldSL, double newSL) throws JFException {
        if (openedChart == null) {
            return;
        }

        ITriangleChartObject orderSLTriangle = factory.createTriangle("Triangle " + shorLineCounter++, 
                changedOrder.getFillTime(), changedOrder.getOpenPrice(), tick.getTime(), oldSL, tick.getTime(), newSL);
              
        Color lineColor = oldSL > newSL ? Color.RED : Color.GREEN;
        orderSLTriangle.setColor(lineColor);
        orderSLTriangle.setLineStyle(LineStyle.SOLID);
        orderSLTriangle.setLineWidth(1);
        orderSLTriangle.setStickToCandleTimeEnabled(false);
        openedChart.addToMainChart(orderSLTriangle);

        //drawing text
        String breakTextOldSL = String.format(" Old SL: %.5f", oldSL);
        String breakTextNewSL = String.format(" New SL: %.5f", newSL);
        double textVerticalPosition = oldSL > newSL ? newSL - myInstrument.getPipValue() : newSL + myInstrument.getPipValue();
        ITextChartObject textOldSL = factory.createText("textKey1" + textCounterOldSL++, tick.getTime(), oldSL);
        ITextChartObject textNewSL = factory.createText("textKey2" + textCounterNewSL++, tick.getTime(), newSL);
        textOldSL.setText(breakTextOldSL);
        textNewSL.setText(breakTextNewSL);
        textOldSL.setStickToCandleTimeEnabled(false);
        textNewSL.setStickToCandleTimeEnabled(false);
        openedChart.addToMainChart(textOldSL);
        openedChart.addToMainChart(textNewSL);
    }

    //10. Create a method that check the chart. 
private boolean checkChart(IChart chart) {
    if (chart == null) {
        printMeError("chart for " + myInstrument + " not opened!");
        return false;
    }
    if (chart.getSelectedOfferSide() != this.myOfferSide) {
        printMeError("chart offer side is not " + this.myOfferSide);
        return false;
    }
    if (chart.getSelectedPeriod() != this.myPeriod) {
        printMeError("chart period is not " + this.myPeriod);
        return false;
    }
    if (chart.getFilter() != this.filter) {
        printMeError("chart filter is not " + this.filter);
        return false;
    }
    return true;
}

    
}
