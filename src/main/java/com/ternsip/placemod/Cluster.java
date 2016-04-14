package com.ternsip.placemod;

import java.util.ArrayList;

/**
 * Created by TrnMain on 08.04.2016.
 */
class Cluster {

    private double chance = 0;
    private String name = "";
    private ArrayList<Structure> structures = new ArrayList<Structure>();

    public Cluster(String name) {
        this.name = name;
    }

    public Cluster(Cluster cluster) {
        this.chance = cluster.getChance();
        this.name = cluster.getName();
        this.structures.addAll(cluster.getStructures());
    }

    public Cluster add(Structure structure) {
        structures.add(structure);
        return this;
    }

    public Cluster add(ArrayList<Structure> structures) {
        this.structures.addAll(structures);
        return this;
    }

    void setChance(double chance) {
        this.chance = chance;
    }

    double getChance() {
        return chance;
    }

    public String getName() {
        return name;
    }

    ArrayList<Structure> getStructures() {
        return structures;
    }

}
