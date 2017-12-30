package edu.byu.ece.rapidSmith.util

interface Ordinable<T : Ordinable<T>> : Comparable<T> {
	val ordinal: Int

	override fun compareTo(t: T): Int {
		return ordinal - t.ordinal
	}
}
