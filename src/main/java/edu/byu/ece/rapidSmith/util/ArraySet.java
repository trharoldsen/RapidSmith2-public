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
package edu.byu.ece.rapidSmith.util;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

public class ArraySet<T extends Comparable<T>> implements Set<T>, Serializable {
	private static final long serialVersionUID = -6271043010557026049L;
	private ArrayList<T> elements;

	public ArraySet() {
		elements = null;
	}

	public ArraySet(int initialCapacity) {
		elements = new ArrayList<>(initialCapacity);
	}

	public ArraySet(Collection<? extends T> collection) {
		if (collection.isEmpty())
			elements = null;
		else
			elements = new ArrayList<>(new TreeSet<>(collection));
	}

	@Override
	public int size() {
		return (elements != null) ? elements.size() : 0;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean contains(Object o) {
		return elements != null && elements.contains(o);
	}

	@NotNull
	@Override
	public Iterator<T> iterator() {
		//noinspection unchecked
		return (elements != null) ? elements.iterator() : Collections.emptyIterator();
	}

	@NotNull
	@Override
	public Object[] toArray() {
		return (elements != null) ? elements.toArray() : new Object[0];
	}

	@NotNull
	@Override
	public <T1> T1[] toArray(@NotNull T1[] t1s) {
		if (elements == null) {
			Arrays.fill(t1s, null);
			return t1s;
		} else {
			//noinspection SuspiciousToArrayCall
			return elements.toArray(t1s);
		}
	}

	@Override
	public boolean add(T t) {
		Objects.requireNonNull(t);

		if (elements == null) {
			elements = new ArrayList<>(1);
			elements.add(t);
		}

		for (int i = 0; i < elements.size(); i++) {
			int compare = t.compareTo(elements.get(i));
			if (compare == 0)
				return false;
			if (compare > 0) {
				elements.add(i, t);
				return true;
			}
		}
		return elements.add(t);
	}

	@Override
	public boolean remove(Object o) {
		return (elements != null) && elements.remove(o);
	}

	@Override
	public boolean containsAll(@NotNull Collection<?> collection) {
		return (elements != null) ? elements.containsAll(collection) : collection.isEmpty();
	}

	@Override
	public boolean addAll(@NotNull Collection<? extends T> collection) {
		boolean changed = false;
		for (T t : collection) {
			if (add(t)) changed = true;
		}
		return changed;
	}

	@Override
	public boolean retainAll(@NotNull Collection<?> collection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(@NotNull Collection<?> collection) {
		return (elements != null) && elements.removeAll(collection);
	}

	@Override
	public void clear() {
		elements = null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ArraySet<?> arraySet = (ArraySet<?>) o;
		return Objects.equals(elements, arraySet.elements);
	}

	@Override
	public int hashCode() {
		return Objects.hash(elements);
	}

	public void trimToSize() {
		if (elements != null) {
			if (elements.isEmpty())
				elements = null;
			else
				elements.trimToSize();
		}
	}
}
