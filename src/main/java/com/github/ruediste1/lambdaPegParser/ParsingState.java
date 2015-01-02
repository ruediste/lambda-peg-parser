package com.github.ruediste1.lambdaPegParser;

public class ParsingState<TSelf extends ParsingState<TSelf>> implements
		Cloneable {

	private int index;

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TSelf clone() {
		try {
			return (TSelf) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
