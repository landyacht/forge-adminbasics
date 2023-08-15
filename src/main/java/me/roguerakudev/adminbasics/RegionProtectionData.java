package me.roguerakudev.adminbasics;

import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import org.apache.logging.log4j.LogManager;

import java.util.HashMap;
import java.util.Map;

public class RegionProtectionData extends WorldSavedData {
    private static final String IDENTIFIER = "AB_RGN_PRTCN";

    private Map<String, ProtectedRegion> regionByName;

    public RegionProtectionData() {
        super(IDENTIFIER);
        regionByName = new HashMap<>();
    }

    boolean isPosInProtectedRegion(BlockPos pos) {
        return regionByName.values().stream().anyMatch(region -> region.containsPos(pos));
    }

    ImmutableList<ProtectedRegion> getAllRegions() {
        return ImmutableList.copyOf(regionByName.values());
    }

    boolean addRegion(ProtectedRegion region) {
        if (regionByName.containsKey(region.name)) {
            return false;
        }
        regionByName.put(region.name, region);
        markDirty();
        return true;
    }

    boolean deleteRegion(String regionName) {
        ProtectedRegion removed = regionByName.remove(regionName);
        if (removed == null) {
            return false;
        }
        markDirty();
        return true;
    }

    ProtectedRegion getRegion(String regionName) {
        return regionByName.get(regionName);
    }

    boolean updateRegion(ProtectedRegion region) {
        if (!regionByName.containsKey(region.name)) {
            return false;
        }
        regionByName.put(region.name, region);
        markDirty();
        return true;
    }

    @Override
    public void read(CompoundNBT nbt) {
        LogManager.getLogger().info("read: incoming nbt is " + nbt);

        regionByName = new HashMap<>();

        for (String regionName : nbt.keySet()) {
            INBT regionNbt = nbt.get(regionName);
            if (!(regionNbt instanceof IntArrayNBT)) {
                throw new IllegalStateException(IDENTIFIER + " NBT under tag '" + regionName + "' was not of type IntArrayNBT");
            }

            IntArrayNBT regionCoordsNbt = (IntArrayNBT) regionNbt;
            if (regionCoordsNbt.size() != 4) {
                throw new IllegalStateException(IDENTIFIER + " NBT under tag '" + regionName + "' did not have length 4");
            }

            regionByName.put(regionName, new ProtectedRegion(regionName,
                    regionCoordsNbt.get(0).getInt(),
                    regionCoordsNbt.get(1).getInt(),
                    regionCoordsNbt.get(2).getInt(),
                    regionCoordsNbt.get(3).getInt()
            ));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        for (ProtectedRegion region : regionByName.values()) {
            IntArrayNBT coords = new IntArrayNBT(new int[] { region.x1, region.z1, region.x2, region.z2 });
            nbt.put(region.name, coords);
        }

        LogManager.getLogger().info("write: outgoing nbt is " + nbt);
        return nbt;
    }

    static RegionProtectionData getForWorld(World world) {
        DimensionSavedDataManager savedDataManager = ((ServerWorld) world).getSavedData();
        RegionProtectionData result = savedDataManager.getOrCreate(RegionProtectionData::new, IDENTIFIER);
        return result;
    }

    static void saveForWorld(World world) {
        DimensionSavedDataManager savedDataManager = ((ServerWorld) world).getSavedData();
        savedDataManager.save();
    }
}
