package edu.byu.ece.rapidSmith.device

import edu.byu.ece.rapidSmith.util.Ordinable

typealias PinIndex = Int
typealias SiteIndex = Int
typealias BelIndex = Int

data class TileNodeConnection(
	override val ordinal: Int,
	val sourceWire: TileWireTemplate,
	val sinkWire: TileWireTemplate
): Ordinable<TileNodeConnection>

data class SiteNodeConnection(
	override val ordinal: Int,
	val sourceWire: SiteWireTemplate,
	val sinkWire: SiteWireTemplate
): Ordinable<SiteNodeConnection>

data class ExternalSitePinConnection(
	override val ordinal: Int,
	val tileOffset: Offset,
	val siteType: SiteType,
	val siteIndex: SiteIndex,
	val pinIndex: PinIndex
): Ordinable<ExternalSitePinConnection>

data class InternalSitePinConnection(
	override val ordinal: Int,
	val siteType: SiteType,
	val pinIndex: PinIndex
): Ordinable<InternalSitePinConnection>

data class BelPinConnection(
	override val ordinal: Int,
	val siteType: SiteType,
	val belIndex: BelIndex,
	val pinIndex: PinIndex
): Ordinable<BelPinConnection>


