package com.ternsip.placemod;


public class Report {

    String result = "[" + Placemod.MODNAME + " v" + Placemod.VERSION + "]";

    public Report add(String key, String value) {
        result += "[" + key + " = " + value + "]";
        return this;
    }

    public void print() {
        System.out.println(result);
    }

}
