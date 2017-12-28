/*
 * Copyright (c) 2016 Brigham Young University
 *
 * This file is part of the BYU RapidSmith Tools.
 *
 * BYU RapidSmith Tools is free software: you may redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * BYU RapidSmith Tools is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU General Public License is included with the BYU
 * RapidSmith Tools. It can be found at doc/LICENSE.GPL3.TXT. You may
 * also get a copy of the license at <http://www.gnu.org/licenses/>.
 */
package edu.byu.ece.rapidSmith.device;

import edu.byu.ece.rapidSmith.WireTemplate;
import edu.byu.ece.rapidSmith.util.ArraySet;

import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Function;

/**
 * DO NOT USE THIS CLASS!  This class was specially developed for the Device 
 * wire connections hash map.  It is specifically optimized for that purpose.
 * Created on: Mar 18, 2011
 */
public class WireHashMap<T extends WireTemplate> implements Serializable {
	/**
	 * The default initial capacity - MUST be a power of two.
	 */
	private static final int DEFAULT_INITIAL_CAPACITY = 16;

	/**
	 * The maximum capacity, used if a higher value is implicitly specified
	 * by either of the constructors with arguments.
	 * MUST be a power of two <= 1<<30.
	 */
	private static final int MAXIMUM_CAPACITY = 1 << 30;

	/**
	 * The load factor used when none specified in constructor.
	 */
	private static final float DEFAULT_LOAD_FACTOR = 0.85f;
	private static final long serialVersionUID = -1457572150224638234L;

	/**
	 * The keys table. Length MUST Always be a power of two.
	 */
	private T[] keys;
	
	/**
	 * The corresponding values table.
	 */
	public ArraySet<WireConnection<T>>[] values;
	
	/**
	 * The number of key-value mappings contained in this map.
	 */
	private int size;

	// These variables are used to track the whether the caches are up to date.
	// A cache is up to date if it is equivalent to the wireHashMapModification
	// value.  Any put operation updates the wireHashMapModification value
	private transient int wireHashMapModification = 0;
	private transient int keySetCacheModification = -1; // initialize as out of date
	private transient int valuesCacheModification = -1; // initialize as out of date

	// Caches are stored as soft references to avoid being a memory drain
	// when not in use.
	private transient SoftReference<Set<T>> keySetCache;
	private transient SoftReference<List<ArraySet<WireConnection<T>>>> valuesCache;

	/**
	 * The next size value at which to resize (capacity * load factor).
	 * @serial
	 */
	private int threshold;

	/**
	 * The load factor for the hash table.
	 *
	 * @serial
	 */
	private final float loadFactor;

	private Integer hash;

	/** This map requires an initial capacity.  This map will not grow.
	 * @param capacity the set capacity for this hash map
	 * @param loadFactor the load factor for this hash map before growing
	 */
	@SuppressWarnings("unchecked")
	private WireHashMap(int capacity, float loadFactor){
		if (capacity < 0)
			throw new IllegalArgumentException("Illegal initial capacity: " +
											   capacity);
		if (capacity > MAXIMUM_CAPACITY)
			capacity = MAXIMUM_CAPACITY;

		// Find a power of 2 >= initialCapacity
		int finalCapacity = 4;
		while (finalCapacity < capacity)
			finalCapacity <<= 1;
		
		this.loadFactor = loadFactor;
		threshold = (int)(finalCapacity * loadFactor);
		
		keys = (T[]) new Object[finalCapacity];
		values = (ArraySet<WireConnection<T>>[]) new Object[finalCapacity];
		size = 0;
	}

	private WireHashMap(float loadFactor) {
		this.loadFactor = loadFactor;
	}

	public WireHashMap(){
		this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Returns the number of key-value mappings in this map.
	 *
	 * @return the number of key-value mappings in this map
	 */
	public int size() {
		return size;
	}


	public boolean isEmpty() {
		return size == 0;
	}

	private int indexFor(T key) {
		int i = key.hashCode() & (keys.length-1);
		while(keys[i] != null && !keys[i].equals(key)) {
			i+=3;
			if(i >= keys.length) i=i&3;
		}
		return i;
	}
	
	public ArraySet<WireConnection<T>> get(T key){
		int i = indexFor(key);
		if (keys[i] == null)
			return null;
		//noinspection unchecked
		return values[i];
	}

	public void put(T key, ArraySet<WireConnection<T>> value){
		int i = indexFor(key);
		if(keys[i] == null)
			size++;
		keys[i] = key;
		values[i] = value;
		wireHashMapModification++;

		if(size > threshold){
			grow();
		}
	}

	@SuppressWarnings("unchecked")
	private void grow(){
		int newCapacity = keys.length*2;
		threshold = (int)(newCapacity * loadFactor);
		Object[] oldKeys = keys;
		Object[] oldValues = values;
		keys = (T[]) new Object[newCapacity];
		Arrays.fill(keys, null);
		values = (ArraySet<WireConnection<T>>[]) new Object[newCapacity];
		size = 0;
		for(int i=0; i < oldKeys.length; i++){
			if(oldKeys[i] != null){
				//noinspection unchecked
				put((T) oldKeys[i], (ArraySet<WireConnection<T>>) oldValues[i]);
			}
		}
	}
	
	public Set<T> keySet(){
		// check if the cached keySets are current
		Set<T> keySet = keySetCache == null ? null : keySetCache.get();
		if (keySet != null && keySetCacheModification == wireHashMapModification)
			return keySet;
		keySetCacheModification = wireHashMapModification;

		// build the keyset cache
		keySet = new HashSet<>();
		for (Object key : keys) {
			if (key != null)
				//noinspection unchecked
				keySet.add((T) key);
		}
		keySetCache = new SoftReference<>(keySet);
		return keySet;
	}
	
	public List<ArraySet<WireConnection<T>>> values(){
		// check if the cached values are current;
		List<ArraySet<WireConnection<T>>> valuesList = valuesCache == null ? null : valuesCache.get();
		if (valuesList != null && valuesCacheModification == wireHashMapModification)
			return valuesList;
		valuesCacheModification = wireHashMapModification;

		// build the values cache
		valuesList = new ArrayList<>(size);
		for (int i = 0; i < keys.length; i++) {
			if(keys[i] != null)
				//noinspection unchecked
				valuesList.add(values[i]);
		}
		valuesCache = new SoftReference<>(valuesList);
		return valuesList;
	}

	public ArraySet<WireConnection<T>> computeIfAbsent(T key, Function<? super T, ArraySet<WireConnection<T>>> f) {
		ArraySet<WireConnection<T>> v = get(key);
		if (v != null) return v;

		ArraySet<WireConnection<T>> newv = f.apply(key);
		put(key, newv);
		return newv;
	}

	@Override
	public int hashCode() {
		if (hash != null)
			return hash;
		hash = 0;
		for (T i : keySet()) {
			hash += i.hashCode() * 8191;
			hash += Objects.hashCode(get(i));
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;

		WireHashMap other = (WireHashMap) obj;
		if (!keySet().equals(other.keySet())) {
			return false;
		}
		for (T key : keySet()) {
			//noinspection unchecked
			if (!Objects.equals(get(key), other.get(key))) {
				return false;
			}
		}
		return true;
	}

	private static class WireHashMapReplace<T extends WireTemplate> implements Serializable {
		private static final long serialVersionUID = -7369854899201714510L;
		private int arrSize;
		private float loadFactor;
		private T[] keys;
		private int[] indices;
		private ArraySet<WireConnection<T>>[] values;

		@SuppressWarnings("unused")
		private WireHashMap<T> readResolve() {
			WireHashMap<T> whm = new WireHashMap<>(loadFactor);
			//noinspection unchecked
			whm.keys = (T[]) new Object[arrSize];
			Arrays.fill(whm.keys, null);
			//noinspection unchecked
			whm.values = (ArraySet<WireConnection<T>>[]) new Object[arrSize];

			for (int i = 0; i < keys.length; i++) {
				whm.keys[indices[i]] = keys[i];
				whm.values[indices[i]] = values[i];
			}

			whm.size = keys.length;
			whm.threshold = (int) (arrSize * loadFactor);
			return whm;
		}
	}

	@SuppressWarnings("unused")
	private WireHashMapReplace writeReplace() {
		WireHashMapReplace repl = new WireHashMapReplace();
		repl.arrSize = keys.length;
		repl.keys = new WireTemplate[size];
		repl.indices = new int[size];
		//noinspection unchecked
		repl.values = (ArraySet<WireConnection<WireTemplate>>[]) new Object[size];

		int j = 0;
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] != null) {
				repl.keys[j] = keys[i];
				repl.indices[j] = i;
				repl.values[j] = values[i];
				j++;
			}
		}

		repl.loadFactor = loadFactor;

		return repl;
	}
}
