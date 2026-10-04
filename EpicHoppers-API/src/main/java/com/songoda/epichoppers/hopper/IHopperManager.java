package com.songoda.epichoppers.hopper;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public interface IHopperManager {

    Hopper addHopperAt(Location location, UUID createFor);

    Hopper removeHopperAt(Location location);

    Hopper getHopperAt(Location location);

    Hopper getHopperAt(Location location, UUID createForIfNotExists);

    boolean isHopper(Location location);

    Map<Location, ? extends Hopper> getHoppers();
}
