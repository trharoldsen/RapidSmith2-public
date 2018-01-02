package edu.byu.ece.rapidSmith.device

typealias PinIndex = Int
typealias SiteIndex = Int
typealias BelIndex = Int

data class TileNodeConnection(
	val sourceWire: TileWireTemplate,
	val sinkWire: TileWireTemplate
)

data class SiteNodeConnection(
	val sourceWire: SiteWireTemplate,
	val sinkWire: SiteWireTemplate
)

data class ExternalSitePinConnection(
	val siteType: SiteType,
	val siteIndex: SiteIndex,
	val pinIndex: PinIndex
)

data class InternalSitePinConnection(
	val siteType: SiteType,
	val pinIndex: PinIndex
)

data class BelPinConnection(
	val siteType: SiteType,
	val belIndex: BelIndex,
	val pinIndex: PinIndex
)


