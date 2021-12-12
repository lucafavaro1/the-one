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
 * Map based movement model that uses predetermined paths within the map area.
 * Nodes using this model (can) stop on every route waypoint and find their
 * way to next waypoint using {@link DijkstraPathFinder}. There can be
 * different type of routes; see {@link #ROUTE_TYPE_S}.
 */
public class EmployeesMovement extends MapBasedMovement implements
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

    private ArrayList<Coord> allOfficeCoords = new ArrayList<>();

    public ArrayList<Coord> getAllOfficeCoords() {
        return allOfficeCoords;
    }

    public HashMap<String, Coord> getMatchLabelWithCoord() {
        return matchLabelWithCoord;
    }

    /**
     * Creates a new movement model based on a Settings object's settings.
     *
     * @param settings The Settings object where the settings are read from
     */
    public EmployeesMovement(Settings settings) {
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
    protected EmployeesMovement(EmployeesMovement proto) {
        super(proto);
        fillTheHashMap();
        this.route = proto.allRoutes.get(proto.nextRouteIndex).replicate();
        this.firstStopIndex = proto.firstStopIndex;
        this.destination = proto.destination;
        this.lastWaypoint = proto.lastWaypoint;
        this.allOfficeCoords = proto.allOfficeCoords;
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

        final double curTime = SimClock.getTime();
        Random rng = new Random();
        int percentage;
        String destinationType = "";

        // 8am - 12am
        if (curTime > 0 && curTime <= 14400)
            destinationType = "office".concat(String.valueOf(rng.nextInt(6) + 1));
        else if (curTime > 14400 && curTime <= 18000) {
            percentage = getRandomPercentage();
            if (percentage <= 70)
                destinationType = "entranceN";
            else if (percentage <= 85)
                destinationType = "cafeteria";
            else
                destinationType = "office".concat(String.valueOf(rng.nextInt(6) + 1));
        }
        // 12 am - 1 pm
        else if (curTime > 18000 && curTime <= 36000) {
            destinationType = "office".concat(String.valueOf(rng.nextInt(6) + 1));
        }
        // 1 pm - 6pm
        else if (curTime > 36000) {
            percentage = getRandomPercentage();
            if (percentage <= 80)
                destinationType = "entranceN";
            else if (percentage <= 90)
                destinationType = "entranceW";
            else if (percentage <= 95)
                destinationType = "entranceS";
            else
                destinationType = "entranceE";
        }

        destination = getCoordFromLabel(destinationType);

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

    private int getRandomPercentage() {
        Random rand = new Random();
        return rand.nextInt(100);
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

            double fraction = getRandomFraction();
            Coord location;

            if (fraction < 0.8) {
                location = getCoordFromLabel("entranceN");
            } else if (fraction < 0.85) {
                location = getCoordFromLabel("entranceE");
            } else if (fraction < 0.95) {
                location = getCoordFromLabel("entranceW");
            } else {
                location = getCoordFromLabel("entranceS");
            }

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
    public EmployeesMovement replicate() {
        return new EmployeesMovement(this);
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

        fillAllOfficeCoords();

        String file = "data/MIProject/cafeteriaN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("cafeteria", temp1);

        file = "data/MIProject/HS1_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS1", temp1);

        file = "data/MIProject/HS2_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS2", temp1);

        file = "data/MIProject/HS3_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS3", temp1);

        file = "data/MIProject/tutorial1N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial1", temp1);

        file = "data/MIProject/tutorial2N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial2", temp1);

        file = "data/MIProject/tutorial3N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial3", temp1);

        file = "data/MIProject/tutorial4N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial4", temp1);

        file = "data/MIProject/computerlabN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("computerlab", temp1);

        file = "data/MIProject/libraryN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("library", temp1);

        // ENTRANCES

        file = "data/MIProject/cafeteriaN.wkt";
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

    public void fillAllOfficeCoords() {
        String file = "data/MIProject/offices_patch.wkt";
        List<MapRoute> temp = MapRoute.readRoutes(file, 1, getMap());
        for (int i = 0; i < 6; i++) {
            Coord temp1 = temp.get(i).getStops().get(0).getLocation();
            allOfficeCoords.add(temp1);
        }
    }


}
