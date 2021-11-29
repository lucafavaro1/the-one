/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import java.util.*;
import java.util.concurrent.TimeUnit;

import core.SettingsError;
import core.SimClock;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;
import core.Coord;
import core.Settings;
import movement.map.SimMap;

import javax.xml.stream.Location;

/**
 * Map based movement model that uses predetermined paths within the map area.
 * Nodes using this model (can) stop on every route waypoint and find their
 * way to next waypoint using {@link DijkstraPathFinder}. There can be
 * different type of routes; see {@link #ROUTE_TYPE_S}.
 */
public class MIRouteMovement extends MapBasedMovement implements
        SwitchableMovement {

    public static final String ACTIVE_SETTING = "rwpActivePeriod";

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
     * destination file
     */
    public static final String DESTINATION = "destination";

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

    // adding the activity period feature in the MapRouteMovement
    private final double activeStart1;
    private final double activeEnd1;
    private final double activeStart2;
    private final double activeEnd2;

    //==========================================================================//
    // Implementation - activity periods
    //==========================================================================//
    @Override
    public boolean isActive() {
        final double curTime = SimClock.getTime();
        return ((curTime >= this.activeStart1) && (curTime <= this.activeEnd1)) ||
                ((curTime >= this.activeStart2) && (curTime <= this.activeEnd2));
    }

    @Override
    public double nextPathAvailable() {
        final double curTime = SimClock.getTime();
        if (curTime < this.activeStart1) {
            return this.activeStart1;
        } else if (curTime > activeEnd1 && curTime < this.activeStart2) {
            return this.activeStart2;
        } else if (curTime > this.activeEnd2) {
            return Double.MAX_VALUE;
        }
        return curTime;
    }

    /**
     * Creates a new movement model based on a Settings object's settings.
     *
     * @param settings The Settings object where the settings are read from
     */
    public MIRouteMovement(Settings settings) {
        super(settings);
        fillTheHashMap();
        String fileName = settings.getSetting(ROUTE_FILE_S);
        int type = settings.getInt(ROUTE_TYPE_S);
        allRoutes = MapRoute.readRoutes(fileName, type, getMap());
        nextRouteIndex = 0;
        // definition of the destination
        destination = allRoutes.get(0).getStops().get(allRoutes.get(0).getNrofStops() - 1).getLocation();

        pathFinder = new DijkstraPathFinder(getOkMapNodeTypes());
        this.route = this.allRoutes.get(this.nextRouteIndex).replicate();
        if (this.nextRouteIndex >= this.allRoutes.size()) {
            this.nextRouteIndex = 0;
        }

        if (settings.contains(DESTINATION)) {
            String label = settings.getSetting(DESTINATION);
            destination = getCoordFromLabel(label);
        }


        if (settings.contains(ROUTE_FIRST_STOP_S)) {
            this.firstStopIndex = settings.getInt(ROUTE_FIRST_STOP_S);
            if (this.firstStopIndex >= this.route.getNrofStops()) {
                throw new SettingsError("Too high first stop's index (" +
                        this.firstStopIndex + ") for route with only " +
                        this.route.getNrofStops() + " stops");
            }
        }

        // adding the activity period feature in the MapRouteMovement
        final double[] active = settings.getCsvDoubles(ACTIVE_SETTING, 4);
        this.activeStart1 = active[0];
        this.activeEnd1 = active[1];
        this.activeStart2 = active[2];
        this.activeEnd2 = active[3];
    }

    /**
     * Copyconstructor. Gives a route to the new movement model from the
     * list of routes and randomizes the starting position.
     *
     * @param proto The MapRouteMovement prototype
     */
    protected MIRouteMovement(MIRouteMovement proto) {
        super(proto);
        fillTheHashMap();
        this.route = proto.allRoutes.get(proto.nextRouteIndex).replicate();
        this.firstStopIndex = proto.firstStopIndex;
        // adding the activity period feature in the MapRouteMovement
        this.activeStart1 = proto.activeStart1;
        this.activeEnd1 = proto.activeEnd1;
        this.activeStart2 = proto.activeStart2;
        this.activeEnd2 = proto.activeEnd2;
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
        // lastWaypoint is the first node, destination is the point specified above in the constructor
        MapNode thisNode = map.getNodeByCoord(lastWaypoint);

        final double curTime = SimClock.getTime();
        // TODO: implement change of destination based on simulation time + schedule scenario
        /*
        In my opinion, changing destinations should be done here,
        but movement and activity periods should be managed in DTNHost class.

        for example: choose lunch for the nodes based on probability
            - check that it's lunch time (3000s from the beginning, for example)
            - generate a random number between 0 and 100
            - if 0-70 - go out through entrance N
            - if 70-85 - go to cafeteria
            - if 85 to 100 - stay where you are
         */
        if (curTime >= 3000 && curTime < 5000) {
            Random rand = new Random();
            int number = rand.nextInt(100);

            if (number < 70) {
                destination = getCoordFromLabel("entranceN");
            } else if (number < 85) {
                destination = getCoordFromLabel("cafeteria");
            } else {
                destination = thisNode.getLocation();
            }
        } else {
            // if it's not lunch time, nodes will randomly move between lecture halls
            destination = getCoordFromLabel(getRandomLabelOfType(LocationType.LECTURE_HALL));
        }


        MapNode destinationNode = map.getNodeByCoord(destination);

        List<MapNode> nodes = pathFinder.getShortestPath(thisNode,
                destinationNode);

        for (MapNode node : nodes) { // create a Path from the shortest path
            p.addWaypoint(node.getLocation());
        }

        lastWaypoint = destination.clone();
        lastMapNode = destinationNode;

        // this won't work, need to use activity periods for pause times (because it's real-time timeout)
//        try {
//            TimeUnit.SECONDS.sleep(2);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }


//        destination = getCoordFromLabel(getRandomLabelOfType(LocationType.LECTURE_HALL));

        return p;
    }

    /**
     * Returns the first stop on the route
     */
    @Override
    public Coord getInitialLocation() {
        if (lastMapNode == null) {
            lastMapNode = route.nextStop();
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
    public MIRouteMovement replicate() {
        return new MIRouteMovement(this);
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

        String file = "data/example/cafeteriaN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("cafeteria", temp1);

        file = "data/example/HS1_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS1", temp1);

        file = "data/example/HS2_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS2", temp1);

        file = "data/example/HS3_N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("HS3", temp1);

        file = "data/example/tutorial1N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial1", temp1);

        file = "data/example/tutorial2N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial2", temp1);

        file = "data/example/tutorial3N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial3", temp1);

        file = "data/example/tutorial4N.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("tutorial4", temp1);

        file = "data/example/computerlabN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("computerlab", temp1);

        file = "data/example/libraryN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(temp.get(0).getNrofStops() - 1).getLocation();
        matchLabelWithCoord.put("library", temp1);

        // ENTRANCES

        file = "data/example/cafeteriaN.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceN", temp1);

        file = "data/example/cafeteriaE.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceE", temp1);

        file = "data/example/cafeteriaW.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceW", temp1);

        file = "data/example/cafeteriaS.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        temp1 = temp.get(0).getStops().get(0).getLocation();
        matchLabelWithCoord.put("entranceS", temp1);

        // OFFICES
        file = "data/example/offices_patch.wkt";
        temp = MapRoute.readRoutes(file, 1, getMap());
        for (int i = 0; i < 6; i++) {
            temp1 = temp.get(i).getStops().get(0).getLocation();
            matchLabelWithCoord.put("office".concat(String.valueOf(i + 1)), temp1);
        }

    }

    public enum LocationType {
        ENTRANCE, OFFICE, TUTORIAL, LECTURE_HALL,
        LIBRARY, COMP_LAB, CAFETERIA
    }

    /**
     * Function to obtain a random label (offices are excluded)
     * Don't know if wil be useful or not :)
     *
     * @return the label chosen
     */
    public String randomLabel() {
        Random rand = new Random();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("cafeteria");
        labels.add("computerlab");
        labels.add("tutorial1");
        labels.add("tutorial2");
        labels.add("tutorial3");
        labels.add("tutorial4");
        labels.add("HS1");
        labels.add("HS2");
        labels.add("HS3");
        labels.add("library");
        return labels.get(rand.nextInt(10));
    }

    /**
     * Function to obtain a random location label based on its type
     *
     * @return the label chosen
     */
    public String getRandomLabelOfType(LocationType type) {

        Random rand = new Random();
        ArrayList<String> labels = new ArrayList<>();

        switch (type) {
            case LIBRARY:
                return "library";
            case COMP_LAB:
                return "computerlab";
            case TUTORIAL:
                labels.add("tutorial1");
                labels.add("tutorial2");
                labels.add("tutorial3");
                labels.add("tutorial4");
                return labels.get(rand.nextInt(4));
            case LECTURE_HALL:
                labels.add("HS1");
                labels.add("HS2");
                labels.add("HS3");
                return labels.get(rand.nextInt(3));
            case OFFICE:
                labels.add("office1");
                labels.add("office2");
                labels.add("office3");
                labels.add("office4");
                labels.add("office5");
                labels.add("office6");
                return labels.get(rand.nextInt(6));
            default:
                return "cafeteria";

        }
    }
}
