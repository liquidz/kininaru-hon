package com.uo.liquidz.util;

public interface Callback<V> {

	public abstract void onSuccess(V arg);

	public abstract void onFail();

}