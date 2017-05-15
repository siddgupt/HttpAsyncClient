package com.snapdeal.pool;

import java.util.LinkedList;
import java.util.List;

public class ObjectPooler<T> {
	
	private int poolsz;
	private ObjectFactory<T> factory;
	
	private List<T> pool;
	private LinkedList<T> freeList;
	private volatile boolean isClosing = false;	
	
	public ObjectPooler(int poolsz, ObjectFactory<T> factory){
		this.poolsz = poolsz;
		this.factory = factory;
		setupPool();
	}
	
	public T acquire() throws NoFreeResource{
		if(isClosing)
			throw new NoFreeResource();
		
		T obj = freeList.poll();
		if(obj == null)
			throw new NoFreeResource();
		
		return obj;
	}
	
	public void release(T obj){
		freeList.addLast(obj);
	}
	
	public void refresh(T obj) {
		factory.refresh(obj);
		pool.remove(obj);
		T newobj = factory.getNewInstance();
		pool.add(newobj);
		freeList.add(newobj);
	}
	
	public void close(){
		this.isClosing = true;
		
		for(T obj : pool){
			factory.refresh(obj);
		}
	}
	
	private void setupPool(){
		pool = new LinkedList<T>();
		freeList = new LinkedList<T>();
		
		for(int i = 0; i < poolsz; i++){
			T obj = factory.getNewInstance();
			pool.add(obj);
			freeList.add(obj);
		}
	}
	
	public static interface ObjectFactory<T> {
		T getNewInstance();
		void refresh(T obj);
	}
	
	public static class NoFreeResource extends Exception {
		
	}
	
}
