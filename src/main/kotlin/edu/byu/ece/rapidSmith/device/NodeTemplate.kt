package edu.byu.ece.rapidSmith.device

import edu.byu.ece.rapidSmith.util.ArraySet
import edu.byu.ece.rapidSmith.util.Ordinable

class TileNodeTemplate(
	override val ordinal: Int,
	val wires: HashMap<TileWireTemplate, Offset>,
	val connections: ArraySet<TileNodeConnection>,
	val rconnections: ArraySet<TileNodeConnection>,
	val sitePins: ArraySet<ExternalSitePinConnection>,
	val rsitePins: ArraySet<ExternalSitePinConnection>
): Ordinable<TileNodeTemplate> {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as TileNodeTemplate

		return ordinal == other.ordinal
	}

	override fun hashCode(): Int {
		return ordinal
	}
}

class SiteNodeTemplate(
	override val ordinal: Int,
	val wires: ArraySet<SiteWireTemplate>,
	val connections: ArraySet<SiteNodeConnection>,
	val sitePin: InternalSitePinConnection?,
	val belPin: BelPinConnection?,
	val rconnections: ArraySet<SiteNodeConnection>,
	val rsitePin: InternalSitePinConnection?,
	val rbelPin: BelPinConnection?
): Ordinable<SiteNodeTemplate> {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as SiteNodeTemplate

		return ordinal == other.ordinal
	}

	override fun hashCode(): Int {
		return ordinal
	}
}

data class Offset(val rows: Int, val columns: Int)

