package edu.byu.ece.rapidSmith.device

sealed class Node {
	abstract val wires: Collection<Wire>

	/**
	 * Return connection linking this wire to other wires in the same hierarchy.
	 */
	abstract fun getWireConnections(): Collection<Connection>

	/**
	 * Returns the connected site pins for each possible type of the connected site.
	 * @return all connected sites pins of this wire
	 */
	abstract fun getAllConnectedPins(): Collection<SitePin>

	/**
	 * Returns connection linking this wire to another wire in a different
	 * hierarchical level through a pin.
	 */
	abstract fun getConnectedPin(): SitePin?

	/**
	 * Returns connection linking this wire to another wire in a different
	 * hierarchical level through a pin.
	 */
	abstract fun getTerminal(): BelPin?

	/**
	 * Returns connection linking this wire to its drivers in the same hierarchy.
	 */
	abstract fun getReverseWireConnections(): Collection<Connection>

	/**
	 * Returns the connected site pins for each possible type of the connected site.
	 * @return all connected sites pins of this wire
	 */
	abstract fun getAllReverseConnectedPins(): Collection<SitePin>

	/**
	 * Return connection linking this wire to its drivers in the different
	 * levels of hierarchy.
	 */
	abstract fun getReverseConnectedPin(): SitePin?

	/**
	 * Returns the sources (BelPins) which drive this wire.
	 */
	abstract fun getSource(): BelPin?
}

/**
 * A wire inside a tile but outside a site.  This is part of the general
 * routing circuitry.  TileWires are composed of the tile the wire exists in
 * and the enumeration identifying the individual wire.
 */
class TileNode internal constructor(
	private val rootTile: Tile,
	internal val template: TileNodeTemplate
) : Node() {
	override val wires: Collection<TileWire>
		get() {
			return template.wires.map { (t, o) -> TileWire(getOffsetTile(o), t) }
		}

	/**
	 * Returns all sink connections within and between tiles.
	 */
	override fun getWireConnections(): Collection<Connection> {
		return template.connections
			.map { Connection.TileWireConnection(getOffsetTile(it.sourceWire), it) }
	}

	override fun getAllConnectedPins(): Collection<SitePin> {
		return template.sitePins
			.map { getOffsetTile(it.tileOffset).getSite(it.siteIndex).getPin(it.siteType, it.pinIndex) }
	}

	/**
	 * Returns the connection into a primitive site that this wire drives.
	 */
	override fun getConnectedPin(): SitePin? {
		return template.sitePins
			.map {
				getOffsetTile(it.tileOffset).getSite(it.siteIndex) to it }
			.singleOrNull { it.first.type == it.second.siteType }
			?.let { it.first.getPin(it.second.siteType, it.second.pinIndex) }
	}

	/**
	 * Always returns null.
	 */
	override fun getTerminal(): BelPin? {
		return null
	}

	/**
	 * Returns connection to all connections within or between tiles which drive
	 * this wire.
	 */
	override fun getReverseWireConnections(): Collection<Connection> {
		return template.rconnections
			.map { Connection.TileWireConnection(getOffsetTile(it.sourceWire), it) }
	}

	override fun getAllReverseConnectedPins(): Collection<SitePin> {
		return template.rsitePins
			.map { getOffsetTile(it.tileOffset).getSite(it.siteIndex).getPin(it.siteType, it.pinIndex) }
	}

	/**
	 * Returns the site pin connection driving this wire.
	 */
	override fun getReverseConnectedPin(): SitePin? {
		return template.rsitePins
			.map {
				getOffsetTile(it.tileOffset).getSite(it.siteIndex) to it }
			.singleOrNull { it.first.type == it.second.siteType }
			?.let { it.first.getPin(it.second.siteType, it.second.pinIndex) }
	}

	/**
	 * Always returns null.
	 */
	override fun getSource(): BelPin? {
		return null
	}

	/**
	 * Tests if the object is equal to this wire.  Wires are equal if they share
	 * the same tile and wire enumeration.
	 * @return true if *obj* is the same wire as this wire
	 */
	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val o = other as TileNode?
		return this.rootTile == o!!.rootTile && this.template == o.template
	}

	override fun hashCode(): Int {
		return template.hashCode() * 8191 + rootTile.hashCode()
	}

	override fun toString(): String {
		return "TileNode: ${rootTile.name} ${template.ordinal}"
	}

	private fun getOffsetTile(source: TileWireTemplate): Tile {
		val device = rootTile.device
		val offset = template.wires[source]!!
		return device.getTile(rootTile.row + offset.rows, rootTile.column + offset.columns)!!
	}

	private fun getOffsetTile(offset: Offset): Tile {
		val device = rootTile.device
		return device.getTile(rootTile.row + offset.rows, rootTile.column + offset.columns)!!
	}
}

/**
 * A wire inside a tile but outside a site.  This is part of the general
 * routing circuitry.  TileWires are composed of the tile the wire exists in
 * and the enumeration identifying the individual wire.
 */
class SiteNode internal constructor(
	private val site: Site,
	internal val template: SiteNodeTemplate
) : Node() {
	override val wires: Collection<SiteWire>
		get() {
			return template.wires.map { t -> SiteWire(site, t) }
		}

	/**
	 * Returns all sink connections within and between tiles.
	 */
	override fun getWireConnections(): Collection<Connection> {
		return template.connections.map { Connection.SiteWireConnection(site, it) }
	}

	/**
	 * Returns connection to all connections within or between tiles which drive
	 * this wire.
	 */
	override fun getReverseWireConnections(): Collection<Connection> {
		return template.rconnections.map { Connection.ReverseSiteWireConnection(site, it) }
	}

	override fun getAllConnectedPins(): Collection<SitePin> {
		return getConnectedPin()?.let { listOf(it) } ?: emptyList()
	}

	/**
	 * Returns the connection into a primitive site that this wire drives.
	 */
	override fun getConnectedPin(): SitePin? {
		val sitePin = template.sitePin ?: return null
		return site.getPin(sitePin.siteType, sitePin.pinIndex)!!
	}

	override fun getAllReverseConnectedPins(): Collection<SitePin> {
		return getReverseConnectedPin()?.let { listOf(it) } ?: emptyList()
	}

	/**
	 * Returns the site pin connection driving this wire.
	 */
	override fun getReverseConnectedPin(): SitePin? {
		val sitePin = template.rsitePin ?: return null
		return site.getPin(sitePin.siteType, sitePin.pinIndex)
	}

	/**
	 * Always returns null.
	 */
	override fun getTerminal(): BelPin? {
		val belPin = template.belPin ?: return null
		return site.getBel(belPin.siteType, belPin.belIndex).getPin(belPin.pinIndex)!!
	}

	/**
	 * Always returns null.
	 */
	override fun getSource(): BelPin? {
		val belPin = template.rbelPin ?: return null
		return site.getBel(belPin.siteType, belPin.belIndex).getPin(belPin.pinIndex)!!
	}

	/**
	 * Tests if the object is equal to this wire.  Wires are equal if they share
	 * the same tile and wire enumeration.
	 * @return true if *obj* is the same wire as this wire
	 */
	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val o = other as SiteNode?
		return this.site == o!!.site && this.template == o.template
	}

	override fun hashCode(): Int {
		return template.hashCode() * 8191 + site.hashCode()
	}

	override fun toString(): String {
		return "SiteNode: ${site.name} ${template.ordinal}"
	}
}
