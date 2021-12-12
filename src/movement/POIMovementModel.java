/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import java.util.*;

import core.SettingsError;
import core.SimClock;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;
import core.Coord;
import core.Settings;
import movement.map.SimMap;

/**
 * POI = Points Of Interest, nodes moving for the whole simulation time between
 * points of interest. Created in order to have always some nodes moving in the hallways
 * during the simulation time (to improve realism)
 */
public class POIMovementModel extends MapBasedMovement implements
        SwitchableMovement {

    /**
     * Per node group setting used for selecting a route file ({@value})
     */
    public static final String ROUTE_FILE_S = "routeFile";

    /**
     * Per node group setting used for selecting a route's type ({@value}).
     * Integer value from {@link MapRoute} class.
     */
    public static final String ROUTE_TYPE_S = "routeType";

    /**
     * Per node group setting for selecting which stop (counting from 0 from
     * the start of the route) should be the first one. By default, or if a
     * negative value is given, a random stop is selected.
     */
    public static final String ROUTE_FIRST_STOP_S = "routeFirstStop";

    /**
     * the Dijkstra shortest path finder
     */
    private DijkstraPathFinder pathFinder;

    /**
     * Prototype's reference to all routes read for the group
     */
    private List<MapRoute> allRoutes = null;
    /**
     * next route's index to give by prototype
     */
    private Integer nextRouteIndex;
    /**
     * Index of the first stop for a group of nodes (or -1 for random)
     */
    private int firstStopIndex = -1;

    /**
     * Route of the movement model's instance
     */
    private MapRoute route;

    // attributes to define starting and ending point
    private Coord lastWaypoint;
    private Coord destination;

    private HashMap<String, Coord> matchLabelWithCoord = new HashMap<>();

    /**
     * Creates a new movement model based on a Settings object's settings.
     *
     * @param settings The Settings object where the settings are read from
     */
    public POIMovementModel(Settings settings) {
        super(settings);
        fillTheHashMap();
        String fileName = settings.getSetting(ROUTE_FILE_S);
        int type = settings.getInt(ROUTE_TYPE_S);
        allRoutes = MapRoute.readRoutes(fileName, type, getMap());

        nextRouteIndex = 0;


        pathFinder = new DijkstraPathFinder(getOkMapNodeTypes());

        this.route = this.allRoutes.get(this.nextRouteIndex).replicate();
        if (this.nextRouteIndex >= this.allRoutes.size()) {
            this.nextRouteIndex = 0;
        }


        if (settings.contains(ROUTE_FIRST_STOP_S)) {
            this.firstStopIndex = settings.getInt(ROUTE_FIRST_STOP_S);
            if (this.firstStopIndex >= this.route.getNrofStops()) {
                throw new SettingsError("Too high first stop's index (" +
                        this.firstStopIndex + ") for route with only " +
                        this.route.getNrofStops() + " stops");
            }
        }

    }

    /**
     * Copyconstructor. Gives a route to the new movement model from the
     * list of routes and randomizes the starting position.
     *
     * @param proto The MapRouteMovement prototype
     */
    protected POIMovementModel(POIMovementModel proto) {
        super(proto);
        fillTheHashMap();
        this.allRoutes = proto.allRoutes;
        this.route = proto.allRoutes.get(proto.nextRouteIndex).replicate();
        this.firstStopIndex = proto.firstStopIndex;
        this.destination = proto.destination;
        this.lastWaypoint = proto.lastWaypoint;
        this.nextRouteIndex = proto.nextRouteIndex;

        if (firstStopIndex < 0) {
            /* set a random starting position on the route */
            this.route.setNextIndex(rng.nextInt(route.getNrofStops() - 1));
        } else {
            /* use the one defined in the config file */
            this.route.setNextIndex(this.firstStopIndex);
        }

        this.pathFinder = proto.pathFinder;

        proto.nextRouteIndex++; // give routes in order
        if (proto.nextRouteIndex >= proto.allRoutes.size()) {
            proto.nextRouteIndex = 0;
        }
    }

    @Override
    public Path getPath() {
        Path p = new Path(generateSpeed());

        SimMap map = super.getMap();
        MapNode thisNode = map.getNodeByCoord(lastWaypoint);
        double probability;

        final double curTime = SimClock.getTime();

        // 8am - 8pm
        if (curTime > 0 && curTime <= 42000) {
            int x = rng.nextInt(allRoutes.size()-1);
            destination = allRoutes.get(x).getStops().get(rng.nextInt(allRoutes.get(x).getNrofStops()-1)).getLocation();
        }

        // EXIT around 8pm
        else if (curTime > 42000) {
            probability = getRandomFraction();
            if(thisNode.getLocation().equals(getCoordFromLabel("entranceN"))
                || thisNode.getLocation().equals(getCoordFromLabel("entranceS"))
                || thisNode.getLocation().equals(getCoordFromLabel("entranceE"))
                || thisNode.getLocation().equals(getCoordFromLabel("entranceW")))
                destination = thisNode.getLocation();
            else if (probability < 0.8) {
                destination = getCoordFromLabel("entranceN");
            } else if (probability < 0.90) {
                destination = getCoordFromLabel("entranceE");
            } else if (probability < 0.95) {
                destination = getCoordFromLabel("entranceW");
            } else {
                destination = getCoordFromLabel("entranceS");
            }
        }

        MapNode destinationNode = map.getNodeByCoord(destination);

        List<MapNode> nodes = pathFinder.getShortestPath(thisNode,
                destinationNode);

        for (MapNode node : nodes) { // create a Path from the shortest path
            p.addWaypoint(node.getLocation());
        }

        lastWaypoint = destination.clone();
        lastMapNode = destinationNode;

        return p;
    }


    private double getRandomFraction() {
        Random rand = new Random();
        return rand.nextDouble();
    }

    /**
     * Returns the first stop on the route
     */
    @Override
    public Coord getInitialLocation() {
        if (lastMapNode == null) {

            Coord location;

            location = getMap().getNodes().get(rng.nextInt(getMap().getNodes().size())).getLocation();

            lastMapNode = new MapNode(location);
        }
        this.lastWaypoint = lastMapNode.getLocation();

        return lastMapNode.getLocation().clone();
    }

    @Override
    public Coord getLastLocation() {
        if (lastMapNode != null) {
            return lastMapNode.getLocation().clone();
        } else {
            return null;
        }
    }


    @Override
    public POIMovementModel replicate() {
        return new POIMovementModel(this);
    }

    /**
     * Returns the list of stops on the route
     *
     * @return The list of stops
     */
    public List<MapNode> getStops() {
        return route.getStops();
    }

    /**
     * Match the name of the place with the corresponding coordinates
     *
     * @param label name of the place
     * @return coordinate of the place
     */
    public Coord getCoordFromLabel(String label) {
        return matchLabelWithCoord.get(label);
    }

    /**
     * Fill the hashmap with the coordinates + names of the points of interest
     */
    public void fillTheHashMap() {
        List<MapRoute> temp;
        Coord temp1;

        // ENTRANCES

        String file = "data/MIProject/cafeteriaN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceN", temp1);

        file = "data/MIProject/cafeteriaE.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceE", temp1);

        file = "data/MIProject/cafeteriaW.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceW", temp1);

        file = "data/MIProject/cafeteriaS.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceS", temp1);

        // OFFICES
        file = "data/MIProject/offices_patch.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        for (int i = 0; i < 6; i++) {
            temp1 = temp.get(i).getStops().get(0).getLocation();
            matchLabelWithCoord.put("office".concat(String.valueOf(i + 1)), temp1);
        }

    }

}
