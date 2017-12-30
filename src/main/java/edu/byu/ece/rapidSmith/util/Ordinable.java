package edu.byu.ece.rapidSmith.util;

import org.jetbrains.annotations.NotNull;

public interface Ordinable<T extends Ordinable<T>> extends Comparable<T> {
	int getOrdinal();

	@Override
	default int compareTo(@NotNull T t) {
		return getOrdinal() - t.getOrdinal();
	}
}
