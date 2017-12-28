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

import edu.byu.ece.rapidSmith.device.Connection.SiteToTileConnection;
import edu.byu.ece.rapidSmith.device.Connection.SiteWireConnection;
import edu.byu.ece.rapidSmith.util.ArraySet;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

/**
 *
 */
public class SiteWire implements Wire, Serializable {

	private static final long serialVersionUID = -3466670995491249683L;
	private final Site site;
	private final SiteWireTemplate template;

	public SiteWire(Site site, SiteWireTemplate template) {
		this.site = site;
		this.template = template;
	}

	@Override
	public Site getSite() {
		return site;
	}

	public SiteType getSiteType() {
		return template.getSiteType();
	}

	@Override
	public Tile getTile() {
		return site.getTile();
	}

	public SiteWireTemplate getTemplate() {
		return template;
	}

	@Override
	public String getName() {
		return template.getName();
	}

	@Override
	public String getFullName() {
		return getSite().getName() + "/" + getName();
	}

	@Override
	public int ordinal() {
		return template.ordinal();
	}

	@Override
	public Collection<Connection> getWireConnections() {
		ArraySet<WireConnection<SiteWireTemplate>> wcs = site.getWireConnections(template);
		if (wcs == null)
			return Collections.emptyList();

		return wcs.stream()
				.map(wc -> new SiteWireConnection(this, wc))
				.collect(Collectors.toList());
	}
	
	@SuppressWarnings("unchecked")
	public ArraySet<WireConnection<SiteWireTemplate>> getWireConnectionsSet() {
		return site.getWireConnections(template);
	}

	@Override
	public Collection<Connection> getPinConnections() {
		SitePin pin = getConnectedPin();
		if (pin == null)
			return emptyList();
		Connection c = new SiteToTileConnection(pin);
		return singleton(c);
	}

	@Override
	public Collection<SitePin> getAllConnectedPins() {
		return singleton(getConnectedPin());
	}

	@Override
	public SitePin getConnectedPin() {
		SitePin sitePin = site.getSitePinOfInternalWire(template);
		if (sitePin == null || !sitePin.isOutput())
			return null;
		return sitePin;
	}

	@Override
	public Collection<Connection> getTerminals() {
		BelPin pin = getTerminal();
		if (pin == null)
			return emptyList();
		Connection c = new Connection.Terminal(pin);
		return singleton(c);
	}

	@Override
	public BelPin getTerminal() {
		BelPin belPin = site.getBelPinOfWire(template);
		if (belPin == null || !belPin.isInput())
			return null;
		return belPin;
	}

	@Override
	public Collection<Connection> getReverseWireConnections() {
		ArraySet<WireConnection<SiteWireTemplate>> wcs = site.getReverseConnections(template);
		if (wcs == null)
			return Collections.emptyList();

		return wcs.stream()
			.map(wc -> new SiteWireConnection(this, wc))
			.collect(Collectors.toList());
	}
	
	@SuppressWarnings("unchecked")
	public ArraySet<WireConnection<SiteWireTemplate>> getReverseWireConnectionsSet() {
		return site.getReverseConnections(template);
	}

	@Override
	public Collection<SitePin> getAllReverseSitePins() {
		return singleton(getReverseConnectedPin());
	}

	@Override
	public Collection<Connection> getReversePinConnections() {
		SitePin pin = getReverseConnectedPin();
		if (pin == null)
			return emptyList();
		Connection c = new SiteToTileConnection(pin);
		return singleton(c);
	}

	@Override
	public SitePin getReverseConnectedPin() {
		SitePin sitePin = site.getSitePinOfInternalWire(template);
		if (sitePin == null || !sitePin.isInput())
			return null;
		return sitePin;
	}

	@Override
	public Collection<Connection> getSources() {
		BelPin pin = getSource();
		if (pin == null)
			return emptyList();
		Connection c = new Connection.Terminal(pin);
		return singleton(c);
	}

	@Override
	public BelPin getSource() {
		BelPin belPin = site.getBelPinOfWire(template);
		if (belPin == null || !belPin.isOutput())
			return null;
		return belPin;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		// don't need to test site type since wire enums are unique for each type
		final SiteWire other = (SiteWire) obj;
		return this.site.equals(other.site)
				&& this.template.equals(other.template);
	}

	@Override
	public int hashCode() {
		return template.hashCode() * 8191 + site.hashCode();
	}

	@Override
	public String toString() {
		return site.getName() + " " + template.getName();
	}
}
