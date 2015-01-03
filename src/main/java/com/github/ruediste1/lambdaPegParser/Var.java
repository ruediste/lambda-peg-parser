package com.github.ruediste1.lambdaPegParser;

/**
 * A Mutable variable
 */
public class Var<T> {

	private T value;

	public Var() {
	}

	public Var(T value) {
		super();
		this.value = value;
	}

	public static <T> Var<T> of(T value) {
		return new Var<>(value);
	}

	public T getValue() {
		return value;
	}

	public void setValue(T value) {
		this.value = value;
	}
}
