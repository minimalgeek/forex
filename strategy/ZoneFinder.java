package jforex;

import java.util.*;

import com.dukascopy.api.*;
import com.dukascopy.api.drawings.*;
import com.dukascopy.api.drawings.IChartObjectFactory;
import com.dukascopy.api.drawings.IHorizontalLineChartObject;
import java.awt.Color;
import com.dukascopy.api.indicators.IIndicator;

public class ZoneFinder {
    
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
    
    public void ZoneFinder(IContext context, 
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