package proto.wrapper;


import proto.HouseProtoMessage.HouseInfoProto;

import java.util.Objects;

/**
 * Wrapper for class HouseInfoProto
 * Used for override hash and equals method, such that it checks only the id and not every field.
 */
public class HouseInfo {

    private final HouseInfoProto wrapped;

    public HouseInfo(HouseInfoProto wrapped) {
        this.wrapped = wrapped;
    }

    public HouseInfoProto get() {
        return wrapped;
    }


    /**
     * Allow compare between HouseInfo and HouseInfo, HouseInfoProto or String based ONLY on the id field
     * @param o can be either a HouseInfo object, a HouseInfoProto object or a String object
     * @return true if this.HouseInfoProto.id equals (HouseInfo.)(HouseInfoProto.)id, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        Object castedObj = o;
        if (this == o) return true;
        if (o == null) return false;
        if (o.getClass() == HouseInfo.class) {
            castedObj = ((HouseInfo) o).wrapped.getId();
        } else if (o.getClass() == HouseInfoProto.class) {
            castedObj = ((HouseInfoProto) o).getId();
        }
        return this.wrapped.getId().equals(castedObj);
    }

    /**
     * Calculate the hash using only the id of the HouseInfoProto wrapped object
     * @return the hash of this.HouseInfoProto.id
     */
    @Override
    public int hashCode() {
        return Objects.hash(wrapped.getId());
    }
}
