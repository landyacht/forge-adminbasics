package me.roguerakudev.adminbasics;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

class ProtectedRegion {
    final String name;
    final int x1, z1, x2, z2;

    ProtectedRegion(String name, int x1, int z1, int x2, int z2) {
        this.name = name;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
    }

    ProtectedRegion(ProtectedRegion toCopy) {
        name = toCopy.name;
        x1 = toCopy.x1;
        z1 = toCopy.z1;
        x2 = toCopy.x2;
        z2 = toCopy.z2;
    }

    ProtectedRegion butWithName(String newName) {
        return new ProtectedRegion(
                newName, x1, z1, x2, z2
        );
    }
    ProtectedRegion butWithX1(int newX1) {
        return new ProtectedRegion(
                name, newX1, z1, x2, z2
        );
    }
    ProtectedRegion butWithZ1(int newZ1) {
        return new ProtectedRegion(
                name, x1, newZ1, x2, z2
        );
    }
    ProtectedRegion butWithX2(int newX2) {
        return new ProtectedRegion(
                name, x1, z1, newX2, z2
        );
    }
    ProtectedRegion butWithZ2(int newZ2) {
        return new ProtectedRegion(
                name, x1, z1, x2, newZ2
        );
    }
    ProtectedRegion butNormalized() {
        if (x1 > x2) {
            return this.butWithX1(x2).butWithX2(x1).butNormalized();
        }
        if (z1 > z2) {
            return this.butWithZ1(z2).butWithZ2(z1);
        }
        return this;
    }

    boolean containsPos(BlockPos pos) {
        return
                   pos.getX() >= x1
                && pos.getX() <= x2
                && pos.getZ() >= z1
                && pos.getZ() <= z2;
    }

    AxisAlignedBB toAABB() {
        return new AxisAlignedBB(
                x1, 0, z1,
                x2, 256, z2
        );
    }

    @Override
    public String toString() {
        return name + " @ X(" + x1 + " -> " + x2 + "), Z(" + z1 + " -> " + z2 + ")";
    }
}
