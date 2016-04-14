package com.ternsip.placemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by TrnMain on 08.04.2016.
 */
class Distributor {

    private HashMap<Integer, ArrayList<Cluster>> biomesClusters = new HashMap<Integer, ArrayList<Cluster>>();

    Distributor(ArrayList<Cluster> clusters) {
        for (Cluster cluster : clusters) {
            HashSet<Integer> biomeStyles = new HashSet<Integer>();
            for (Structure structure : cluster.getStructures()) {
                biomeStyles.add(structure.flags.getInteger("Biome"));
                if (structure.flags.getString("Method").equalsIgnoreCase("Floating")) {
                    biomeStyles.add(Biome.Style.COMMON.value);
                }
            }
            for (Integer biomeStyle : biomeStyles) {
                final Cluster newCluster = new Cluster(cluster);
                if (biomesClusters.containsKey(biomeStyle)) {
                    biomesClusters.get(biomeStyle).add(newCluster);
                } else {
                    biomesClusters.put(biomeStyle, new ArrayList<Cluster>() {{add(newCluster);}});
                }
            }
        }
        double A = Decorator.ratioA;
        double B = Decorator.ratioB;
        for (ArrayList<Cluster> biomeClusters : biomesClusters.values()) {
            long totalBlocks = 0;
            for (Cluster cluster : biomeClusters) {
                for (Structure structure : cluster.getStructures()) {
                    int width = structure.flags.getShort("Width");
                    int height = structure.flags.getShort("Height");
                    int length = structure.flags.getShort("Length");
                    totalBlocks += width * height * length;
                }
            }
            double averageBlocks = totalBlocks / biomeClusters.size();
            double chancesSum = 0;
            for (Cluster cluster : biomeClusters) {
                long weight = 0;
                for (Structure structure : cluster.getStructures()) {
                    int width = structure.flags.getShort("Width");
                    int height = structure.flags.getShort("Height");
                    int length = structure.flags.getShort("Length");
                    weight += width * height * length;
                }
                // f(x) = 2/(1+e^(-x^0.5))-1
                // f(x) = 2 /(1+e^(-A*x^B))-1
                // DEFAULT A = 1, B = 0.5
                //double saturation = 2.0 / (1.0 + Math.exp(-Math.pow(weight / averageBlocks, 0.5))) - 1.0;
                double saturation = 2.0 / (1.0 + Math.exp(-A * Math.pow(weight / averageBlocks, B))) - 1.0;
                double chance = 1.0 - saturation;
                cluster.setChance(chance);
                chancesSum += chance;
            }
            for (Cluster cluster : biomeClusters) {
                cluster.setChance(cluster.getChance() / chancesSum);
            }
        }
    }

    ArrayList<Cluster> getClusters(Biome.Style biome) {
        return biomesClusters.getOrDefault(biome.value, new ArrayList<Cluster>());
    }

}
