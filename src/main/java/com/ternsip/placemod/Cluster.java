package com.ternsip.placemod;

import java.util.ArrayList;

/**
 * Created by TrnMain on 08.04.2016.
 */
class Cluster {

    private double chance = 0;
    private ArrayList<Structure> structures = new ArrayList<Structure>();

    Cluster(ArrayList<Structure> structures) {
        this.structures = structures;
    }

    Cluster(Structure structure) {
        add(structure);
    }

    public void add(Structure structure) {
        structures.add(structure);
    }

    void setChance(double chance) {
        this.chance = chance;
    }

    double getChance() {
        return chance;
    }

    ArrayList<Structure> getStructures() {
        return structures;
    }
}
