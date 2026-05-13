package net.alkeari.geogradient.fabric.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.alkeari.geogradient.GeoGradient;

@Config(name = GeoGradient.MOD_ID)
@SuppressWarnings("CanBeFinal")
public class GeoGradientFabricConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public int globe_size = 10000;

    @ConfigEntry.Gui.Tooltip
    public int border_amplitude = 200;

    @ConfigEntry.Gui.Tooltip
    public double border_frequency = 0.002;

    @ConfigEntry.Gui.Tooltip
    public double spawn_latitude = 0.0;
}
