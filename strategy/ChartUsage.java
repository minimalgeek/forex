package charts;

import com.dukascopy.api.*;
import java.util.HashSet;
import java.util.Set;
//1. Add imports. We will need them later on. 
import com.dukascopy.api.drawings.IChartObjectFactory;
import com.dukascopy.api.drawings.IOhlcChartObject;
import com.dukascopy.api.indicators.OutputParameterInfo.DrawingStyle;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;



public class ChartUsage implements IStrategy{
    
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;    
    private IBar previousBar;  
    private IOrder order;
    //2.define the following instance variables:
    private IChart openedChart;
    private IChartObjectFactory factory;
    private int signals;
                    
    private static final int PREV = 1;
    private static final int SECOND_TO_LAST = 0;
      
    @Configurable(value="Instrument value")
    public Instrument myInstrument = Instrument.EURGBP;
    @Configurable(value="Offer Side value", obligatory=true)
    public OfferSide myOfferSide;
    @Configurable(value="Period value")
    public Period myPeriod = Period.TEN_MINS;
    @Configurable("SMA time period")
    public int smaTimePeriod = 30;    
    //3.add following parameters: 
    @Configurable("Add OHLC Index to chart")
    public boolean addOHLC = true;
    @Configurable("Filter")
    public Filter filter = Filter.WEEKENDS;
    @Configurable("Draw SMA")
    public boolean drawSMA = true;
    @Configurable("Close chart on onStop")
    public boolean closeChart;
    
   
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        //4.initialize the previously defined instance variables:
        this.openedChart = context.getChart(myInstrument);              
        this.factory = openedChart.getChartObjectFactory();
    
        //subscribe an instrument:
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(myInstrument);                     
        context.setSubscribedInstruments(instruments, true);      
    
        //5.Check whether the user wants to add SMA indicator to the chart.
        //If yes, then executea addToChart method. addToChart method returns boolean value
        //if it can add an indicator to the chart. If it cannot add, then an error message is printed to output
        if(drawSMA)
            if(!addToChart(openedChart)){
                printMeError("Indicators did not get plotted on chart. Check the chart values!");
            }
    }

    public void onAccount(IAccount account) throws JFException {
    }

    
    public void onMessage(IMessage message) throws JFException {        
        if(message.getOrder() != null) 
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message.getContent());
    }

    //6.check whether the user have choosed to close chart when the strategy is stopped. 
    public void onStop() throws JFException {    
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {     

        if (!instrument.equals(myInstrument) || !period.equals(myPeriod)) {
            return; //quit
        }                 
    
        int candlesBefore = 2, candlesAfter = 0; 
        previousBar = myOfferSide == OfferSide.ASK ? askBar : bidBar;
        long currBarTime = previousBar.getTime();
        double sma[] = indicators.sma(instrument, period, myOfferSide, IIndicators.AppliedPrice.CLOSE, 
                smaTimePeriod, Filter.NO_FILTER, candlesBefore, currBarTime, candlesAfter);    
        printMe(String.format("Bar SMA Values: Second-to-last = %.5f; Last Completed = %.5f", sma[SECOND_TO_LAST], sma[PREV]));
        
        IEngine.OrderCommand myCommand = null;
        printMe(String.format("Bar SMA Values: Second-to-last = %.5f; Last Completed = %.5f", sma[SECOND_TO_LAST], sma[PREV]));        
        if(sma[PREV] > sma[SECOND_TO_LAST]){
            printMe("SMA in up-trend"); //indicator goes up
            myCommand = IEngine.OrderCommand.BUY;        
        } else if(sma[PREV] < sma[SECOND_TO_LAST]){
            printMe("SMA in down-trend"); //indicator goes down
            myCommand = IEngine.OrderCommand.SELL;        
        } else {
            return;
        }
    
        order = engine.getOrder("MyStrategyOrder");                       
        if(order != null && engine.getOrders().contains(order) && order.getOrderCommand() != myCommand){
            order.close();
            order.waitForUpdate(IOrder.State.CLOSED); //wait till the order is closed
            console.getOut().println("Order " + order.getLabel() + " is closed");            
        } 
        if(order == null || !engine.getOrders().contains(order)){            
            engine.submitOrder("MyStrategyOrder", instrument, myCommand, 0.1);
            //7.we will draw an arrow when makeing an order. 
            //So we need a time of the current bar - to get it, we add one period of a bar to a time of the last completed bar (previousBar). 
            long time = previousBar.getTime() + myPeriod.getInterval();
            //8.creating a IChartObject - an up-array or down-array and add a text label to the array. 
            //finally, add a IChartObject(array in this case) to the chart. 
            IChartObject signal = myCommand.isLong() ? 
                    factory.createSignalUp("signalUpKey" + signals++, time, previousBar.getLow() - instrument.getPipValue()*2)
                    : factory.createSignalDown("signalDownKey" + signals++, time, previousBar.getHigh() + instrument.getPipValue()*2);
            signal.setText(String.format("delta SMA %+.7f", sma[PREV] - sma[SECOND_TO_LAST]), new Font("Monospaced", Font.BOLD, 12));
            openedChart.addToMainChart(signal);                        
        }  

}//end of onBar method
    
    private void printMe(Object toPrint){
        console.getOut().println(toPrint);
    }
    //9. add a new method to print error messages to strategy's output
    private void printMeError(Object o){
        console.getErr().println(o);
    }
            
    //this is the method which checks whether the chart with required parameters is opened.
    //if chart is not opened, then exits and returns false. 
    //method adds a chart indicator (in this exampel - SMA). 
    //check if a user has checked an option to add a OHCL values to chart. If yes, then add values.
    private boolean addToChart(IChart chart){
        if(chart == null){
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
        if(chart.getFilter() != this.filter){
            printMeError("chart filter is not " + this.filter);
            return false;
        }
            
        chart.addIndicator(indicators.getIndicator("SMA"), new Object[] {smaTimePeriod}, 
                new Color[]{Color.GREEN}, new DrawingStyle[]{DrawingStyle.LINE}, new int[]{3});
        
        //if user has choosed to show the OHLC values, then add them to the chart:
        if(addOHLC){
            //If finds an existing ohlc object, then assign this object to the ohlc ref. variable
            IOhlcChartObject ohlc = null;
            for (IChartObject obj : chart.getAll()) {
                if (obj instanceof IOhlcChartObject) {
                    ohlc = (IOhlcChartObject) obj;
                }
            }
            //if cannot find existing ohlc object, then create new one and assign to ohlc ref. variable
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
    
    
}
