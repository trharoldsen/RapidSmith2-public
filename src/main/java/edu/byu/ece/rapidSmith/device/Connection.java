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

import java.io.Serializable;
import java.util.Objects;

import static edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

/**
 *
 */
public abstract class Connection implements Serializable {
	private static final long serialVersionUID = 3236104431137672033L;

	public final static class TileWireConnection extends Connection {
		private static final long serialVersionUID = 7549238102833227662L;
		private final Tile tile;
		private final TileNodeConnection template;

		public TileWireConnection(Tile tile, TileNodeConnection template) {
			this.tile = tile;
			this.template = template;
		}

		@Override
		public TileWire getSourceWire() {
			return new TileWire(tile, template.getSourceWire());
		}

		@Override
		public TileWire getSinkWire() {
			return new TileWire(tile, template.getSinkWire());
		}

		@Override
		public TileNode getSourceNode() {
			TileWireTemplate sourceWire = template.getSourceWire();
			TileNodeTemplate nodeTemplate = tile.getNodeOfWire(sourceWire);
			Device device = tile.getDevice();
			Offset offset = nodeTemplate.getWires().get(sourceWire);
			Tile offsetTile = device.getTile(tile.getRow() - offset.getRows(),
				tile.getColumn() - offset.getColumns());
			return new TileNode(offsetTile, nodeTemplate);
		}

		@Override
		public TileNode getSinkNode() {
			TileWireTemplate sinkWire = template.getSinkWire();
			TileNodeTemplate nodeTemplate = tile.getNodeOfWire(sinkWire);
			Device device = tile.getDevice();
			Offset offset = nodeTemplate.getWires().get(sinkWire);
			Tile offsetTile = device.getTile(tile.getRow() - offset.getRows(),
				tile.getColumn() - offset.getColumns());
			return new TileNode(offsetTile, nodeTemplate);
		}

		@Override
		public boolean isRouteThrough() {
			Device device = tile.getDevice();
			return device.isRouteThrough(template.getSourceWire(), template.getSinkWire());
		}

		@Override
		public PIP getPip() {
			return new PIP(getSourceWire(), getSinkWire());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			TileWireConnection that = (TileWireConnection) o;
			return Objects.equals(tile, that.tile) &&
				Objects.equals(template, that.template);
		}

		@Override
		public int hashCode() {

			return Objects.hash(tile, template);
		}
	}

	public final static class ReverseTileWireConnection extends Connection {
		private static final long serialVersionUID = -3585646572632532927L;
		private final Tile tile;
		private final TileNodeConnection template;

		public ReverseTileWireConnection(Tile tile, TileNodeConnection template) {
			this.tile = tile;
			this.template = template;
		}

		@Override
		public TileWire getSourceWire() {
			return new TileWire(tile, template.getSourceWire());
		}

		@Override
		public TileWire getSinkWire() {
			return new TileWire(tile, template.getSinkWire());
		}

		@Override
		public TileNode getSourceNode() {
			TileWireTemplate sourceWire = template.getSourceWire();
			TileNodeTemplate nodeTemplate = tile.getNodeOfWire(sourceWire);
			Device device = tile.getDevice();
			Offset offset = nodeTemplate.getWires().get(sourceWire);
			Tile offsetTile = device.getTile(tile.getRow() - offset.getRows(),
				tile.getColumn() - offset.getColumns());
			return new TileNode(offsetTile, nodeTemplate);
		}

		@Override
		public TileNode getSinkNode() {
			TileWireTemplate sinkWire = template.getSinkWire();
			TileNodeTemplate nodeTemplate = tile.getNodeOfWire(sinkWire);
			Device device = tile.getDevice();
			Offset offset = nodeTemplate.getWires().get(sinkWire);
			Tile offsetTile = device.getTile(tile.getRow() - offset.getRows(),
				tile.getColumn() - offset.getColumns());
			return new TileNode(offsetTile, nodeTemplate);
		}

		@Override
		public boolean isRouteThrough() {
			Device device = tile.getDevice();
			return device.isRouteThrough(template.getSinkWire(), template.getSourceWire());
		}

		@Override
		public PIP getPip() {
			return new PIP(getSourceWire(), getSinkWire());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ReverseTileWireConnection that = (ReverseTileWireConnection) o;
			return Objects.equals(tile, that.tile) &&
				Objects.equals(template, that.template);
		}

		@Override
		public int hashCode() {

			return Objects.hash(tile, template);
		}
	}

	public final static class SiteWireConnection extends Connection {
		private static final long serialVersionUID = -6889841775729826036L;
		private final Site site;
		private final SiteNodeConnection template;

		public SiteWireConnection(Site site, SiteNodeConnection template) {
			this.site = site;
			this.template = template;
		}

		@Override
		public SiteWire getSourceWire() {
			return new SiteWire(site, template.getSourceWire());
		}

		@Override
		public SiteWire getSinkWire() {
			return new SiteWire(site, template.getSinkWire());
		}

		@Override
		public SiteNode getSourceNode() {
			SiteWireTemplate sourceWire = template.getSourceWire();
			SiteNodeTemplate nodeTemplate = site.getNodeOfWire(sourceWire);
			return new SiteNode(site, nodeTemplate);
		}

		@Override
		public SiteNode getSinkNode() {
			SiteWireTemplate sinkWire = template.getSinkWire();
			SiteNodeTemplate nodeTemplate = site.getNodeOfWire(sinkWire);
			return new SiteNode(site, nodeTemplate);
		}

		@Override
		public boolean isRouteThrough() {
			// bel routethrough
			return site.isRoutethrough(template.getSourceWire(), template.getSinkWire());
		}

		@Override
		public PIP getPip() {
			return new PIP(getSourceWire(), getSinkWire());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			SiteWireConnection that = (SiteWireConnection) o;
			return Objects.equals(site, that.site) &&
				Objects.equals(template, that.template);
		}

		@Override
		public int hashCode() {
			return Objects.hash(site, template);
		}
	}

	public final static class ReverseSiteWireConnection extends Connection {
		private static final long serialVersionUID = -6889841775729826036L;
		private final Site site;
		private final SiteNodeConnection template;

		public ReverseSiteWireConnection(Site site, SiteNodeConnection template) {
			this.site = site;
			this.template = template;
		}

		@Override
		public SiteWire getSourceWire() {
			return new SiteWire(site, template.getSourceWire());
		}

		@Override
		public SiteWire getSinkWire() {
			return new SiteWire(site, template.getSinkWire());
		}

		@Override
		public SiteNode getSourceNode() {
			SiteWireTemplate sourceWire = template.getSourceWire();
			SiteNodeTemplate nodeTemplate = site.getNodeOfWire(sourceWire);
			return new SiteNode(site, nodeTemplate);
		}

		@Override
		public SiteNode getSinkNode() {
			SiteWireTemplate sinkWire = template.getSinkWire();
			SiteNodeTemplate nodeTemplate = site.getNodeOfWire(sinkWire);
			return new SiteNode(site, nodeTemplate);
		}

		@Override
		public boolean isRouteThrough() {
			// bel routethrough
			return site.isRoutethrough(template.getSinkWire(), template.getSourceWire());
		}

		@Override
		public PIP getPip() {
			return new PIP(getSourceWire(), getSinkWire());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			SiteWireConnection that = (SiteWireConnection) o;
			return Objects.equals(site, that.site) &&
				Objects.equals(template, that.template);
		}

		@Override
		public int hashCode() {

			return Objects.hash(site, template);
		}
	}

	public abstract Wire getSourceWire();

	public abstract Wire getSinkWire();

	public abstract Node getSourceNode();

	public abstract Node getSinkNode();

	public abstract boolean isRouteThrough();

	public abstract PIP getPip();
}
