package com.github.s7connector.api;

import java.util.Objects;

public final class ItemKey {
    private final DaveArea area;
    private final int areaNumber, bytes, offset;

    public ItemKey(DaveArea area, int areaNumber, int bytes, int offset) {
        this.area = area;
        this.areaNumber = areaNumber;
        this.bytes = bytes;
        this.offset = offset;
    }

    public DaveArea getArea() {
        return area;
    }

    public int getAreaNumber() {
        return areaNumber;
    }

    public int getBytes() {
        return bytes;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemKey itemKey = (ItemKey) o;
        return areaNumber == itemKey.areaNumber &&
                bytes == itemKey.bytes &&
                offset == itemKey.offset &&
                area == itemKey.area;
    }

    @Override
    public int hashCode() {
        return Objects.hash(area, areaNumber, bytes, offset);
    }
}
