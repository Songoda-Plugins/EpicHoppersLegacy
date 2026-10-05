package com.songoda.epichoppers.containers;

import com.songoda.epichoppers.containers.impl.AdvancedChestImpl;
import com.songoda.epichoppers.containers.impl.EpicFarmingImpl;
import com.songoda.epichoppers.containers.impl.FabledSkyBlockImpl;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.PluginManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ContainerManagerImpl implements ContainerManager {
    private final Set<IContainer> customContainers;

    public ContainerManagerImpl() {
        this.customContainers = new HashSet<>();

        registerCustomContainerImplementation("AdvancedChests", new AdvancedChestImpl());
        registerCustomContainerImplementation("EpicFarming", new EpicFarmingImpl());
        registerCustomContainerImplementation("FabledSkyBlock", new FabledSkyBlockImpl());
    }

    @Override
    public Set<IContainer> getCustomContainerImplementations() {
        return Collections.unmodifiableSet(this.customContainers);
    }

    @Override
    public void registerCustomContainerImplementation(String requiredPlugin, IContainer container) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if (requiredPlugin == null || pluginManager.isPluginEnabled(requiredPlugin)) {
            this.customContainers.add(container);
        }
    }

    @Override
    public void registerCustomContainerImplementation(IContainer container) {
        registerCustomContainerImplementation(null, container);
    }

    @Override
    public CustomContainer getCustomContainer(Block block) {
        for (IContainer container : this.customContainers) {
            CustomContainer customContainer = container.getCustomContainer(block);
            if (customContainer.isContainer()) {
                return customContainer;
            }
        }

        return null;
    }
}
