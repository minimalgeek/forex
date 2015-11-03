/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package onBarExample;

import com.dukascopy.api.*;
import java.util.HashSet;
import java.util.Set;


public class SMASampleTrade implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;    
    private IBar previousBar;  
    private IOrder order;
            
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
    
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        
        //subscribe an instrument:
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(myInstrument);                     
        context.setSubscribedInstruments(instruments, true);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    
    
    /*
     * filter out from all IMessages the messages related with orders and
     * log them to the strategy's output tab
     */
    public void onMessage(IMessage message) throws JFException {        
        if(message.getOrder() != null) 
            printMe("order: " + message.getOrder().getLabel() + " || message content: " + message.getContent());
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    
    
    /*
     * Implement our business logic here. Filter specific instrument and period. 
     * Get the order and check whether the order with the same label exists in open state, 
     * if yes then close it. Make a decision and submit a new order. 
     */    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {     
        
        if (!instrument.equals(myInstrument) || !period.equals(myPeriod)) {
            return; //quit
        }                 
                
        IEngine.OrderCommand myCommand = null;
        int candlesBefore = 2, candlesAfter = 0; 
        //get SMA values of 2nd-to last and last (two last completed) bars
        previousBar = myOfferSide == OfferSide.ASK ? askBar : bidBar;
        long currBarTime = previousBar.getTime();
        double sma[] = indicators.sma(instrument, period, myOfferSide, IIndicators.AppliedPrice.CLOSE, 
                smaTimePeriod, Filter.NO_FILTER, candlesBefore, currBarTime, candlesAfter);
        
        /*print some message so we can later compare the results with a chart,
         *If SMA is up-trend (green line in sample picture) execute BUY order. 
         *If SMA is down-trend (red line in sample picture) execute SELL order.
         */
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
        
        /*check if the order already exists. If exists, then check if the processing order's command is the same as myCommand. 
         * If it is the same, then do nothing (let the order stay in open state and continues to processing).
         * If the order command is different (SMA trend changes direction) from the current order's command,
         * then close the opened order and create new one:
         */
        order = engine.getOrder("MyStrategyOrder");                       
        if(order != null && engine.getOrders().contains(order) && order.getOrderCommand() != myCommand){
            order.close();
            order.waitForUpdate(IOrder.State.CLOSED); //wait till the order is closed
            console.getOut().println("Order " + order.getLabel() + " is closed");            
        } 
        //if the order is new or there is no order with the same label, then create a new order:
        if(order == null || !engine.getOrders().contains(order)){
            engine.submitOrder("MyStrategyOrder", instrument, myCommand, 0.1);            
        }
        
    }
    
    private void printMe(String toPrint){
        console.getOut().println(toPrint);
    }
}
