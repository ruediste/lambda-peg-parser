package com.github.ruediste.lambdaPegParser;

public class ParsingState<TSelf extends ParsingState<TSelf>> implements Cloneable {

    public int index;

    public int minPrecedenceLevel = 0;

    @SuppressWarnings("unchecked")
    @Override
    public TSelf clone() {
        try {
            return (TSelf) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "" + index + "(" + minPrecedenceLevel + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        result = prime * result + minPrecedenceLevel;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParsingState<?> other = (ParsingState<?>) obj;
        return index == other.index && minPrecedenceLevel == other.minPrecedenceLevel;
    }

}
