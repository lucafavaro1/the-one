package movement;

import core.Coord;
import core.Settings;
import core.SimClock;

/**
 * @author teemuk
 */
public class StaticRouter
        extends MovementModel {

    //==========================================================================//
    // Settings
    //==========================================================================//
    public static final String ACTIVE_SETTING = "rwpActivePeriod";
    public static final String COORD = "coord";

    //==========================================================================//
    // Instance vars
    //==========================================================================//
    private final double activeStart;
    private final double activeEnd;
    private Coord lastWaypoint;
    private Coord routerPosition;
    //==========================================================================//



    //==========================================================================//
    // Implementation - activity periods
    //==========================================================================//
    @Override
    public boolean isActive() {
        final double curTime = SimClock.getTime();
        return ( curTime >= this.activeStart ) && ( curTime <= this.activeEnd );
    }

    @Override
    public double nextPathAvailable() {
        final double curTime = SimClock.getTime();
        if ( curTime < this.activeStart ) {
            return this.activeStart;
        } else if ( curTime > this.activeEnd ) {
            return Double.MAX_VALUE;
        }
        return curTime;
    }
    //==========================================================================//


    //==========================================================================//
    // Implementation - Basic RWP
    //==========================================================================//
    @Override
    public Path getPath() {
        // NOTE: The path may last beyond the end of the active period.

        final Path p;
        p = new Path( generateSpeed() );

        return p;
    }

    @Override
    public Coord getInitialLocation() {
        return routerPosition;
    }

    @Override
    public MovementModel replicate() {
        return new StaticRouter( this );
    }

    private Coord randomCoord() {
        return new Coord( rng.nextDouble() * super.getMaxX(),
                rng.nextDouble() * super.getMaxY() );
    }

    //==========================================================================//


    //==========================================================================//
    // Constructors
    //==========================================================================//
    public StaticRouter( final Settings settings ) {
        super( settings );
        double [] coordTemp;
        if(settings.contains(COORD)) {
            coordTemp = settings.getCsvDoubles(COORD, 2);
            routerPosition = new Coord(coordTemp[0], coordTemp[1]);
        }

        // Read the activity period from the settings
        final double[] active = settings.getCsvDoubles( ACTIVE_SETTING ,2 );
        this.activeStart = active[ 0 ];
        this.activeEnd = active[ 1 ];
    }

    public StaticRouter( final StaticRouter other ) {
        super( other );
        this.routerPosition = other.routerPosition;
        // Remember to copy our own state
        this.activeStart = other.activeStart;
        this.activeEnd = other.activeEnd;
    }
    //==========================================================================//
}
