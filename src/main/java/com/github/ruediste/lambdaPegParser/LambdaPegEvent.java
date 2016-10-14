package com.github.ruediste.lambdaPegParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LambdaPegEvent<T> {

    private List<Consumer<T>> handlers = new ArrayList<>();

    public void register(Consumer<T> handler) {
        handlers.add(handler);
    }

    public void fire(T argument) {
        handlers.forEach(x -> x.accept(argument));
    }
}
