package com.youdash.model;

/**
 * Outstation legs: origin pickup/drop × destination delivery/collection.
 * <ul>
 *   <li>{@link #DOOR_TO_DOOR} — pickup at sender address, delivered to receiver address</li>
 *   <li>{@link #HUB_TO_HUB} — sender drops at origin hub, receiver collects at destination hub</li>
 *   <li>{@link #DOOR_TO_HUB} — pickup at sender address, receiver collects at destination hub</li>
 *   <li>{@link #HUB_TO_DOOR} — sender drops at origin hub, delivered to receiver address</li>
 * </ul>
 */
public final class FulfillmentType {

    private FulfillmentType() {
    }

    public static final String INCITY = "INCITY";

    public static final String DOOR_TO_DOOR = "DOOR_TO_DOOR";
    public static final String HUB_TO_HUB = "HUB_TO_HUB";
    public static final String DOOR_TO_HUB = "DOOR_TO_HUB";
    public static final String HUB_TO_DOOR = "HUB_TO_DOOR";
}
