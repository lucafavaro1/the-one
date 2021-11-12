package movement;

import core.Coord;
import core.Settings;

import java.util.Arrays;
import java.util.List;

public class MIRouteMovement
extends MovementModel {
    //==========================================================================//
    // Settings
    //==========================================================================//
    /** {@code true} to confine nodes inside the polygon */
    public static final String INVERT_SETTING = "rwpInvert";
    public static final boolean INVERT_DEFAULT = true;
    //==========================================================================//


    //==========================================================================//
    // Instance vars
    //==========================================================================//
    final List<Coord> polygon = Arrays.asList(
            new Coord( 48, 442 ),
            new Coord( 341, 443 ),
            new Coord( 341, 371 ),
            new Coord( 532, 371 ),
            new Coord( 532, 442 ),
            new Coord( 657, 442 ),
            new Coord( 667, 401 ),
            new Coord (700.5488246659241, 356.62790544706394),
            new Coord (749.7767169577378, 315.6046618705526),
            new Coord (788.7487983554234, 303.2976887975992),
            new Coord (864.6417989719694, 311.5023375129015),
            new Coord (897.4603938331784, 350.4744189105872),
            new Coord (901.5627181908295, 389.44650030827296),
            new Coord (901.5627181908295, 442.7767169577376),
new Coord (924.1255021579108, 444.8278791365632),
new Coord (917.972015621434, 559.6929611507948),
new Coord (954.8929348402942, 580.2045829390505),
new Coord (975.4045566285498, 598.6650425484805),
new Coord (977.4557188073754, 631.4836374096895),
new Coord (985.6603675226777, 654.0464213767708),
new Coord (979.5068809862009, 686.8650162379798),
new Coord (965.148745734422, 717.6324489203633),
new Coord (946.6882861249919, 734.0417463509679),
new Coord (915.9208534426085, 738.144070708619),
new Coord (905.6650425484806, 742.2463950662701),
new Coord (905.6650425484806, 879.674261047583),
new Coord (784.6464739977723, 877.6230988687573),
new Coord (784.6464739977723, 951.4649373064776),
new Coord (659.5255810894129, 951.4649373064776),
new Coord (659.5255810894129, 881.7254232264085),
new Coord (571.3256073999136, 879.674261047583),
new Coord (571.3256073999136, 951.4649373064776),
new Coord (425.69309270329853, 951.4649373064776),
new Coord (425.69309270329853, 877.6230988687573),
new Coord (319.03265940436916, 877.6230988687573),
new Coord (319.03265940436916, 967.8742347370821),
new Coord (220.57687482074206, 967.8742347370821),
new Coord (220.57687482074206, 881.7254232264085),
new Coord (154.939685098324, 881.7254232264085),
new Coord (154.939685098324, 967.8742347370821),
new Coord (87.25133319708036, 967.8742347370821),
new Coord (87.25133319708036, 877.6230988687573),
new Coord (48.27925179939463, 877.6230988687573),
new Coord (48.27925179939463, 442.7767169577376)
    );

    private Coord lastWaypoint;
    /** Inverted, i.e., only allow nodes to move inside the polygon. */
    private final boolean invert;
    //==========================================================================//



    //==========================================================================//
    // Implementation
    //==========================================================================//
    @Override
    public Path getPath() {
        // Creates a new path from the previous waypoint to a new one.
        final Path p;
        p = new Path( super.generateSpeed() );
        p.addWaypoint( this.lastWaypoint.clone() );

        // Add only one point. An arbitrary number of Coords could be added to
        // the path here and the simulator will follow the full path before
        // asking for the next one.
        Coord c;
        do {
            c = this.randomCoord();
        } while ( pathIntersects( this.polygon, this.lastWaypoint, c ) );
        p.addWaypoint( c );

        this.lastWaypoint = c;
        return p;
    }

    @Override
    public Coord getInitialLocation() {
        do {
            this.lastWaypoint = this.randomCoord();
        } while ( ( this.invert ) ?
                isOutside( polygon, this.lastWaypoint ) :
                isInside( this.polygon, this.lastWaypoint ) );
        return this.lastWaypoint;
    }

    @Override
    public MovementModel replicate() {
        return new MIRouteMovement( this );
    }

    private Coord randomCoord() {
        return new Coord(
                rng.nextDouble() * super.getMaxX(),
                rng.nextDouble() * super.getMaxY() );
    }
    //==========================================================================//


    //==========================================================================//
    // API
    //==========================================================================//
    public MIRouteMovement( final Settings settings ) {
        super( settings );
        // Read the invert setting
        this.invert = settings.getBoolean( INVERT_SETTING, INVERT_DEFAULT );
    }

    public MIRouteMovement( final MIRouteMovement other ) {
        // Copy constructor will be used when settings up nodes. Only one
        // prototype node instance in a group is created using the Settings
        // passing constructor, the rest are replicated from the prototype.
        super( other );
        // Remember to copy any state defined in this class.
        this.invert = other.invert;
    }
    //==========================================================================//


    //==========================================================================//
    // Private - geometry
    //==========================================================================//
    private static boolean pathIntersects(
            final List <Coord> polygon,
            final Coord start,
            final Coord end ) {
        final int count = countIntersectedEdges( polygon, start, end );
        return ( count > 0 );
    }

    private static boolean isInside(
            final List <Coord> polygon,
            final Coord point ) {
        final int count = countIntersectedEdges( polygon, point,
                new Coord( -10,0 ) );
        return ( ( count % 2 ) != 0 );
    }

    private static boolean isOutside(
            final List <Coord> polygon,
            final Coord point ) {
        return !isInside( polygon, point );
    }

    private static int countIntersectedEdges(
            final List <Coord> polygon,
            final Coord start,
            final Coord end ) {
        int count = 0;
        for ( int i = 0; i < polygon.size() - 1; i++ ) {
            final Coord polyP1 = polygon.get( i );
            final Coord polyP2 = polygon.get( i + 1 );

            final Coord intersection = intersection( start, end, polyP1, polyP2 );
            if ( intersection == null ) continue;

            if ( isOnSegment( polyP1, polyP2, intersection )
                    && isOnSegment( start, end, intersection ) ) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOnSegment(
            final Coord L0,
            final Coord L1,
            final Coord point ) {
        final double crossProduct
                = ( point.getY() - L0.getY() ) * ( L1.getX() - L0.getX() )
                - ( point.getX() - L0.getX() ) * ( L1.getY() - L0.getY() );
        if ( Math.abs( crossProduct ) > 0.0000001 ) return false;

        final double dotProduct
                = ( point.getX() - L0.getX() ) * ( L1.getX() - L0.getX() )
                + ( point.getY() - L0.getY() ) * ( L1.getY() - L0.getY() );
        if ( dotProduct < 0 ) return false;

        final double squaredLength
                = ( L1.getX() - L0.getX() ) * ( L1.getX() - L0.getX() )
                + (L1.getY() - L0.getY() ) * (L1.getY() - L0.getY() );
        if ( dotProduct > squaredLength ) return false;

        return true;
    }

    private static Coord intersection(
            final Coord L0_p0,
            final Coord L0_p1,
            final Coord L1_p0,
            final Coord L1_p1 ) {
        final double[] p0 = getParams( L0_p0, L0_p1 );
        final double[] p1 = getParams( L1_p0, L1_p1 );
        final double D = p0[ 1 ] * p1[ 0 ] - p0[ 0 ] * p1[ 1 ];
        if ( D == 0.0 ) return null;

        final double x = ( p0[ 2 ] * p1[ 1 ] - p0[ 1 ] * p1[ 2 ] ) / D;
        final double y = ( p0[ 2 ] * p1[ 0 ] - p0[ 0 ] * p1[ 2 ] ) / D;

        return new Coord( x, y );
    }

    private static double[] getParams(
            final Coord c0,
            final Coord c1 ) {
        final double A = c0.getY() - c1.getY();
        final double B = c0.getX() - c1.getX();
        final double C = c0.getX() * c1.getY() - c0.getY() * c1.getX();
        return new double[] { A, B, C };
    }
    //==========================================================================//
}
