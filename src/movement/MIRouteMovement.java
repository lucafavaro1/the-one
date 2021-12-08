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
public class MIRouteMovement extends MapBasedMovement implements
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
    private ArrayList<Coord> allStudyCoords = new ArrayList<>();

    /**
     * Getter method for the hashmap
     *
     * @return hashmap containing all the labels and matched coordinates
     */
    public HashMap<String, Coord> getMatchLabelWithCoord() {
        return matchLabelWithCoord;
    }

    /**
     * Getter method for an array of coords that contains all the possible "seats" in the two study zones
     *
     * @return arraylist containing the coordinates
     */
    public ArrayList<Coord> getAllStudyCoords() {
        return allStudyCoords;
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
    protected MIRouteMovement(MIRouteMovement proto) {
        super(proto);
        fillTheHashMap();
        this.route = proto.allRoutes.get(proto.nextRouteIndex).replicate();
        this.firstStopIndex = proto.firstStopIndex;
        this.destination = proto.destination;
        this.lastWaypoint = proto.lastWaypoint;
        this.allStudyCoords = proto.allStudyCoords;
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
        LocationType destinationType = LocationType.ENTRANCE;
        double probability;

        // 8am - 10am
        if (curTime > 0 && curTime <= 7200) {
            probability = getRandomFraction();

            if (probability < 0.45) {
                destinationType = LocationType.TUTORIAL;
            } else if (probability < 0.90) {
                destinationType = LocationType.LECTURE_HALL;
            } else if (probability < 0.92) {
                destinationType = LocationType.LIBRARY;
            } else if (probability < 0.96) {
                destinationType = LocationType.COMP_LAB;
            } else {
                destinationType = LocationType.STUDY_ZONE;
            }

            destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
        }

        // 10am - 12
        else if (curTime > 7200 && curTime <= 14400) {
            probability = getRandomFraction();

            if (probability < 0.35) {
                destinationType = LocationType.TUTORIAL;
            } else if (probability < 0.70) {
                destinationType = LocationType.LECTURE_HALL;
            } else if (probability < 0.76) {
                destinationType = LocationType.LIBRARY;
            } else if (probability < 0.88) {
                destinationType = LocationType.COMP_LAB;
            } else {
                destinationType = LocationType.STUDY_ZONE;
            }

            destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
        }

        // lunch time (12 - 1pm)
        else if (curTime > 14400 && curTime <= 18000) {
            probability = getRandomFraction();

            if (probability < 0.7) {
                destination = getCoordFromLabel("entranceN");
            } else if (probability < 0.85) {
                destination = getCoordFromLabel("cafeteria");
            } else {
                destination = thisNode.getLocation();
            }
        }

        // 1pm - 4pm
        else if (curTime > 18000 && curTime <= 28800) {
            probability = getRandomFraction();
            boolean trick = false;

            if (probability < 0.245) {
                destinationType = LocationType.TUTORIAL;
            } else if (probability < 0.49) {
                destinationType = LocationType.LECTURE_HALL;
            } else if (probability < 0.532) {
                destinationType = LocationType.LIBRARY;
            } else if (probability < 0.616) {
                destinationType = LocationType.COMP_LAB;
            } else if (probability < 0.7) {
                destinationType = LocationType.STUDY_ZONE;
            } else {
                trick = true;
                // those nodes are already at the entrance N (left)
            }

            if (!trick)
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
        }

        // 4pm - 6pm
        else if (curTime > 28800 && curTime <= 36000) {
            probability = getRandomFraction();

            if (probability < 0.105) {
                destinationType = LocationType.TUTORIAL;
            } else if (probability < 0.21) {
                destinationType = LocationType.LECTURE_HALL;
            } else if (probability < 0.308) {
                destinationType = LocationType.LIBRARY;
            } else if (probability < 0.504) {
                destinationType = LocationType.COMP_LAB;
            } else if (probability < 0.7) {
                destinationType = LocationType.STUDY_ZONE;
            } else {
                // those nodes are already at the entrance N (left)
            }

            destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
        }

        // 6pm - 8pm (without exit)
        else if (curTime > 36000 && curTime <= 42000) {
            probability = getRandomFraction();

            if (probability < 0.0175) {
                destinationType = LocationType.TUTORIAL;
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
            } else if (probability < 0.035) {
                destinationType = LocationType.LECTURE_HALL;
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
            } else if (probability < 0.042) {
                destinationType = LocationType.LIBRARY;
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
            } else if (probability < 0.056) {
                destinationType = LocationType.COMP_LAB;
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
            } else if (probability < 0.07) {
                destinationType = LocationType.STUDY_ZONE;
                destination = getCoordFromLabel(getRandomLabelOfType(destinationType));
            } else {
                // 0.93 leave or left already
                // nodes that are at entranceN - already left, remaining leave through different entrances
                if (thisNode.getLocation().equals(getCoordFromLabel("entranceN"))) {
                    destination = getCoordFromLabel("entranceN");
                } else if (probability < 0.814) {
                    destination = getCoordFromLabel("entranceN");
                } else if (probability < 0.86) {
                    destination = getCoordFromLabel("entranceE");
                } else if (probability < 0.95) {
                    destination = getCoordFromLabel("entranceW");
                } else {
                    destination = getCoordFromLabel("entranceS");
                }
            }

        }

        // EXIT around 8pm
        else if (curTime > 42000) {
            probability = getRandomFraction();

            if (probability < 0.28) {
                destination = getCoordFromLabel("entranceN");
            } else if (probability < 0.2975) {
                destination = getCoordFromLabel("entranceE");
            } else if (probability < 0.3325) {
                destination = getCoordFromLabel("entranceW");
            } else if (probability < 0.35) {
                destination = getCoordFromLabel("entranceS");
            } else {
                // hack
                if (!isAtEntrance()) {
                    destination = getCoordFromLabel("entranceN");
                }
            }

        }

        // modelling nodes that are not coming back (after 2pm)
        if (curTime > 21600) {
            if (thisNode.getLocation().equals(getCoordFromLabel("entranceN"))) {
                destination = getCoordFromLabel("entranceN");
            } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceE"))) {
                destination = getCoordFromLabel("entranceE");
            } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceS"))) {
                destination = getCoordFromLabel("entranceS");
            } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceW"))) {
                destination = getCoordFromLabel("entranceW");
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

    private boolean isAtEntrance() {
        boolean atEntrance = false;

        MapNode thisNode = super.getMap().getNodeByCoord(lastWaypoint);

        if (thisNode.getLocation().equals(getCoordFromLabel("entranceN"))) {
            atEntrance = true;
        } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceE"))) {
            atEntrance = true;
        } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceS"))) {
            atEntrance = true;
        } else if (thisNode.getLocation().equals(getCoordFromLabel("entranceW"))) {
            atEntrance = true;
        }

        return atEntrance;
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

            if (fraction < 0.095) {
                location = getCoordFromLabel(getRandomLabelOfType(LocationType.STUDY_ZONE));
            } else if (fraction < 0.815) {
                location = getCoordFromLabel("entranceN");
            } else if (fraction < 0.86) {
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
        if (label.equals("study")) {
            String file = "data/example/openStudy.wkt";
            List<MapRoute> temp = MapRoute.readRoutes(file, 1, getMap());
            Random rand = new Random();
            int whichPath = rand.nextInt(temp.size() - 1) + 1;  // do not change! It's made to avoid the 0 as return value
            return (temp.get(whichPath).getStops().get(rand.nextInt(temp.get(whichPath).getNrofStops())).getLocation());
        }
        return matchLabelWithCoord.get(label);
    }

    /**
     * Fill the hashmap with the coordinates + names of the points of interest
     */
    public void fillTheHashMap() {
        List<MapRoute> temp;
        Coord temp1;

        fillAllStudyCoords();

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

    public void fillAllStudyCoords() {
        String file = "data/example/openStudy.wkt";
        List<MapRoute> temp = MapRoute.readRoutes(file, 1, getMap());
        for (MapRoute mapRoute : temp) {
            for (int j = 0; j < mapRoute.getNrofStops(); j++) {
                allStudyCoords.add(mapRoute.getStops().get(j).getLocation());
            }
        }
    }

    public enum LocationType {
        ENTRANCE, OFFICE, TUTORIAL, LECTURE_HALL,
        LIBRARY, COMP_LAB, CAFETERIA, STUDY_ZONE
    }

    public LocationType getRandomType() {
        Random rand = new Random();
        switch (rand.nextInt(8)) {
            case 0:
                return LocationType.ENTRANCE;
            case 1:
                return LocationType.OFFICE;
            case 2:
                return LocationType.TUTORIAL;
            case 3:
                return LocationType.LECTURE_HALL;
            case 4:
                return LocationType.LIBRARY;
            case 5:
                return LocationType.COMP_LAB;
            case 6:
                return LocationType.CAFETERIA;
            default:
                return LocationType.STUDY_ZONE;
        }

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
            case STUDY_ZONE:
                return "study";
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
