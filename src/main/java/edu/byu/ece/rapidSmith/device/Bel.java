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
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *  Class representing the Basic Elements of Logic.  BELs are the most basic
 *  logical blocks exposed by Xilinx.  Examples include LUTs, FFs and RAMB18s.
 *
 *  As BELs do not have unique names, they are identified by their unique
 *  (site, name) pair.  Due to the number of BELs in a device, BELs are created
 *  upon request using the {@link edu.byu.ece.rapidSmith.device.Site#getBel(java.lang.String)}
 *  method on the site of the desired BEL.
 */
public final class Bel implements Serializable {
	private static final long serialVersionUID = -4092803033961128002L;
	// The backing template for this BEL
	private BelTemplate template;
	// The site the BEL exists in
	private Site site;

	/**
	 * Creates a new BEL in the given site backed by the given template.
	 *
	 * Use Site.getBel to create a new BEL
	 */
	Bel(Site site, BelTemplate template) {
		assert site != null;
		assert template != null;

		this.site = site;
		this.template = template;
	}

	/**
	 * Returns the site this BEL exists in.
	 *
	 * @return the site this BEL exists in
	 */
	public Site getSite() {
		return site;
	}

	/**
	 * Returns the id of this BEL (ex: SLICEL/AFF).
	 *
	 * @return the id of this BEL
	 * @see #getName()
	 * @see #getFullName()
	 */
	public BelId getId() {
		return template.getId();
	}

	/**
	 * Returns the name of the BEL (ex: AFF, F7MUX).
	 *
	 * @return the name of this BEL
	 * @see #getId()
	 * @see #getFullName()
	 */
	public String getName() {
		return template.getId().getName();
	}

	/**
	 * Returns the type of the BEL (ex: LUT6, LUTORMEM5, FF, SELMUX2_1).
	 * <p>
	 * The type is not a part of XDLRC.  It is usually obtained from PlanAhead and
	 * is used to help group similarly functioning BELs.
	 *
	 * @return the type of this BEL
	 */
	public String getType() {
		return template.getType();
	}

	/**
	 * Returns the BEL pin with the specified name.
	 * This method will look in both the sources and the sinks for this pin.
	 *
	 * @param pinName name of the BEL pin to return
	 * @return the BelPin with the given name or null if no pin with the specified
	 *   name exists on this BEL.
	 */
	public BelPin getBelPin(String pinName) {
		BelPinTemplate bpt = template.getPinNamesMap().get(pinName);
		if (bpt == null) return null;
		return new BelPin(this, bpt);
	}

	BelPin getPin(int index) {
		BelPinTemplate bpt = template.getPinTemplates().get(index);
		assert bpt != null;
		return new BelPin(this, bpt);
	}

	/**
	 * Return the source pins of this BEL.
	 *
	 * @return a collection containing the source pins of this BEL
	 */
	public Collection<BelPin> getSources() {
		return template.getPinTemplates().stream()
			.filter(it -> it.getDirection() != PinDirection.OUT)
			.map(it -> new BelPin(this, it))
			.collect(Collectors.toList());
	}

	/**
	 * Return the sink pins of this BEL.
	 *
	 * @return a collection containing the sink pins of this BEL
	 */
	public Collection<BelPin> getSinks() {
		return template.getPinTemplates().stream()
			.filter(it -> it.getDirection() != PinDirection.IN)
			.map(it -> new BelPin(this, it))
			.collect(Collectors.toList());
	}

	public Collection<BelPin> getBelPins() {
		return template.getPinTemplates().stream()
			.map(it -> new BelPin(this, it))
			.collect(Collectors.toList());
	}

	@Override
	public int hashCode() {
		return template.hashCode() * 31 + site.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		final Bel other = (Bel) obj;
		return Objects.equals(this.template, other.template) &&
				Objects.equals(this.site, other.site);
	}

	/**
	 * Returns the full name of this BEL (ex: SLICE_X5Y9/AFF).
	 *
	 * @return the full name of this BEL
	 * @see #getName()
	 * @see #getId()
	 */
	public String getFullName() {
		return site.getName() + "/" + getName();
	}

	@Override
	public String toString() {
		return "Bel{" +
				site.getName() +
				"/" + getName() +
				"}";
	}

	public BelTemplate getTemplate() {
		return template;
	}
}
