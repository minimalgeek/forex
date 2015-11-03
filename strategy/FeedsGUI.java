package feed;

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
import com.dukascopy.api.drawings.IChartDependentChartObject;
import com.dukascopy.api.drawings.ITriangleChartObject;
import com.dukascopy.api.drawings.ITextChartObject;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.IFeedListener;
import com.dukascopy.api.feed.util.RenkoFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;
//1. Add following imports:
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;


public class FeedsGUI implements IStrategy, IFeedListener {

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
    private int uniqueOrderCounter = 1;
    private SMATrend previousSMADirection = SMATrend.NOT_SET;
    private SMATrend currentSMADirection = SMATrend.NOT_SET;
    private Map<IOrder, Boolean> createdOrderMap = new HashMap<IOrder, Boolean>();
    private int shorLineCounter;
    private int textCounterOldSL;
    private int textCounterNewSL;    
    //2. Define following instance variables:
    private boolean dialogClosed = true;
    private JOptionPane optionPane = new JOptionPane(null, JOptionPane.WARNING_MESSAGE);
    private JDialog dialog = optionPane.createDialog(null, null);
    
    
    @Configurable("Add OHLC Index to chart")
    public boolean addOHLC = true;
    @Configurable("Filter")
    public Filter filter = Filter.WEEKENDS;
    @Configurable("Draw SMA")
    public boolean drawSMA = true;
    @Configurable("Close chart on onStop")
    public boolean closeChart;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 10;
    @Configurable("Take profit in pips")
    public int takeProfitPips = 10;
    @Configurable("Break event pips")
    public double breakEventPips = 5;
    @Configurable("SMA time period")
    public int smaTimePeriod = 30;
    @Configurable("Feed type")
    public FeedType myFeed = FeedType.RENKO_2_PIPS_EURUSD_BID;    
    
    private enum SMATrend {

        UP, DOWN, NOT_SET;
    }

    
public enum FeedType {

    RENKO_2_PIPS_EURUSD_BID (new RenkoFeedDescriptor(Instrument.EURUSD, PriceRange.TWO_PIPS, OfferSide.BID)), 
    TIME_BAR_30_SEC_EURUSD_BID (new TimePeriodAggregationFeedDescriptor(Instrument.EURUSD, Period.THIRTY_SECS, OfferSide.BID, Filter.WEEKENDS));

    private final IFeedDescriptor feedDescriptor;

    FeedType(IFeedDescriptor feedDescriptor) {
        this.feedDescriptor = feedDescriptor;
    }
    public IFeedDescriptor getFeedDescriptor(){
        return feedDescriptor;
    }       
}


public void onStart(IContext context) throws JFException {
    this.engine = context.getEngine();
    this.console = context.getConsole();
    this.history = context.getHistory();
    this.context = context;
    this.indicators = context.getIndicators();
    this.userInterface = context.getUserInterface();
    this.openedChart = context.getChart(myFeed.getFeedDescriptor().getInstrument());
    this.factory = openedChart.getChartObjectFactory();

    Set<Instrument> instruments = new HashSet<Instrument>();
    instruments.add(myFeed.getFeedDescriptor().getInstrument());
    context.setSubscribedInstruments(instruments, true);

    if (drawSMA) {
        if (!addToChart(openedChart)) {
            printMeError("Indicators did not get plotted on chart. Check the chart values!");
        }
    }
    
    context.subscribeToFeed(myFeed.getFeedDescriptor(), this);
    
    dialog.setModal(false);

}//end of onStart method

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        if (message.getOrder() != null) {
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message);
        }
    }

    public void onStop() throws JFException {
    }
        
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != myFeed.getFeedDescriptor().getInstrument()) {
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

    
    @Override
    public void onFeedData(IFeedDescriptor feedDescriptor, ITimedData feedData) {
        
        Instrument myInstrument = feedDescriptor.getInstrument();
        OfferSide myOfferSide = feedDescriptor.getOfferSide();
        
        try {
            if(!(feedData instanceof IBar)){
                printMeError("Cannot work with tick feed data");
                return;
            }
            
            IBar bar = (IBar) feedData;

            int candlesBefore = 2, candlesAfter = 0;
            long completedBarTimeL = bar.getTime();
            
            
            Object[] smaObjectsFeed = indicators.calculateIndicator(feedDescriptor, new OfferSide[] { myOfferSide }, "SMA",
            new AppliedPrice[] { AppliedPrice.CLOSE }, new Object[] { smaTimePeriod }, candlesBefore, feedData.getTime(), candlesAfter);
            double[] sma = (double[]) smaObjectsFeed[0]; // sma has just 1 output
            
            
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
                IOrder newOrder = engine.submitOrder("MyStrategyOrder" + uniqueOrderCounter++, myInstrument, myCommand, 0.1, 0, 1, stopLossPrice, takeProfitPrice);
                createdOrderMap.put(newOrder, false);

                //invoke the method that will show up a dialog
                SwingUtilities.invokeLater(new Runnable(){
                    public void run(){
                        showNotification();
                    }
                });

                if (openedChart == null) {
                    return;
                }
                //get current time of a bar from IFeedDescriptor object
                long time = history.getFeedData(feedDescriptor, 0).getTime(); //draw the  ISignalDownChartObject in the current bar
                double space = myInstrument.getPipValue() * 2; //space up or down from bar for ISignalDownChartObject
                IChartDependentChartObject signal = myCommand.isLong()
                        ? factory.createSignalUp("signalUpKey" + signals++, time, bar.getLow() - space)
                        : factory.createSignalDown("signalDownKey" + signals++, time, bar.getHigh() + space);
                signal.setText("MyStrategyOrder" + (uniqueOrderCounter - 1));
                openedChart.addToMainChart(signal);
            }
        } catch (Exception e) {
        }

    }
    
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }

    private void printMe(Object toPrint) {
        console.getOut().println(toPrint);
    }

    private void printMeError(Object o) {
        console.getErr().println(o);
    }

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
        double pipValue = myFeed.getFeedDescriptor().getInstrument().getPipValue();
        double textVerticalPosition = oldSL > newSL ? newSL - pipValue : newSL + pipValue;
        ITextChartObject textOldSL = factory.createText("textKey1" + textCounterOldSL++, tick.getTime(), oldSL);
        ITextChartObject textNewSL = factory.createText("textKey2" + textCounterNewSL++, tick.getTime(), newSL);
        textOldSL.setText(breakTextOldSL);
        textNewSL.setText(breakTextNewSL);
        textOldSL.setStickToCandleTimeEnabled(false);
        textNewSL.setStickToCandleTimeEnabled(false);
        openedChart.addToMainChart(textOldSL);
        openedChart.addToMainChart(textNewSL);
    }

    private boolean checkChart(IChart chart) {
        if (chart == null) {
            printMeError("chart for " + myFeed.getFeedDescriptor().getInstrument() + " not opened!");
            return false;
        }
        if (chart.getSelectedOfferSide() != myFeed.getFeedDescriptor().getOfferSide()) {
            printMeError("chart offer side is not " + myFeed.getFeedDescriptor().getOfferSide());
            return false;
        }
        
        if(chart.getFeedDescriptor().getDataType() == DataType.RENKO){
            if (chart.getPriceRange() != myFeed.getFeedDescriptor().getPriceRange()) {
                printMeError("chart price range is not " + myFeed.getFeedDescriptor().getPriceRange());
                return false;
            }
        } else if (chart.getFeedDescriptor().getDataType() == DataType.TIME_PERIOD_AGGREGATION){
            if(chart.getSelectedPeriod() != myFeed.getFeedDescriptor().getPeriod()){
                printMeError("chart period is not " + myFeed.getFeedDescriptor().getPeriod());
                return false;
            }
        }        
        
        if (chart.getFilter() != this.filter) {
            printMeError("chart filter is not " + this.filter);
            return false;
        }
        return true;
    }//end of checkChart method
    
    
private void showNotification(){        
    String message = "Order MyStrategyOrder" + (uniqueOrderCounter -1) + " created.";      

    if(dialogClosed){            
        dialog.addComponentListener(new ComponentAdapter() {
            public void componentHidden(ComponentEvent e){
                dialogClosed = true;
            }
        });           
        optionPane.setMessage(message);
        dialog.setVisible(true);
        dialogClosed = false;                      
    } else {
        optionPane.setMessage(message);            
        dialog.validate();
    }        
}
    
}
