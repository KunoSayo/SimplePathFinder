package io.github.kunosayo.simplepathfinder.util;


public class UncheckedArrayList<E> {
    private final Object[] arr;

    public UncheckedArrayList(int size) {
        this.arr = new Object[size];
    }

    @SuppressWarnings("unchecked")
    public E get(int idx) {
        return (E) arr[idx];
    }

    public void set(int idx, E e) {
        arr[idx] = e;
    }
}
