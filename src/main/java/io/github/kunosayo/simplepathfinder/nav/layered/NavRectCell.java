package io.github.kunosayo.simplepathfinder.nav.layered;

import io.github.kunosayo.simplepathfinder.nav.finder.CachedVisitObject;

public class NavRectCell extends CachedVisitObject {
    byte minX;
    byte minZ;
    byte maxX;
    byte maxZ;


    public int getArea() {
        return getXLen() * getZLen();
    }

    public int getXLen() {
        return maxX - minX + 1;

    }

    public int getZLen() {
        return maxZ - minZ + 1;
    }

    public boolean isInRegion(int ix, int iz) {
        return ix <= maxX && iz <= maxZ && ix >= minX && iz >= minZ;
    }


}
