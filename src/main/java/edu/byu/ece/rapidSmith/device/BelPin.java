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
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  Class representing a pin on a BEL.
 *
 *  BelPins are created on demand to preserve memory.  BelPins are created via the
 *  {@link edu.byu.ece.rapidSmith.device.Bel#getBelPin(String)} method of the BEL
 *  the pin exists on.
 */
public final class BelPin implements Serializable {
	private static final long serialVersionUID = -402693921202343025L;
	// The BEL this pin exists on
	private final Bel bel;
	// The backing template for this BEL pin
	private final BelPinTemplate template;

	BelPin(Bel bel, BelPinTemplate template) {
		this.bel = bel;
		this.template = template;
	}

	/**
	 * Gets the BEL this pin exists on.
	 * @return the BEL this pin exists on
	 */
	public Bel getBel() {
		return bel;
	}

	/**
	 * Returns the template backing this BelPin.  Shouldn't be needed during
	 * normal use but provided as a luxury.
	 *
	 * @return the template backing this pin
	 */
	public BelPinTemplate getTemplate() {
		return template;
	}

	/**
	 * Returns the name of this pin.
	 *
	 * @return the name of this pin
	 */
	public String getName() {
		return template.getName();
	}

	/**
	 * Returns the site wire connecting to this pin.
	 *
	 * @return the site wire connecting to this pin
	 */
	public SiteWire getWire() {
		Bel bel = getBel();
		return new SiteWire(bel.getSite(), template.getWire());
	}

	public PinDirection getDirection() {
		return template.getDirection();
	}

	/**
	 * Tests if the pin is acts as an output of the BEL this pin exists on.
	 *
	 * @return true if the pin's direction is out or inout
	 */
	public boolean isOutput() {
		PinDirection direction = getDirection();
		return direction == PinDirection.OUT || direction == PinDirection.INOUT;
	}

	/**
	 * Tests if the pin is acts as an input of the BEL this pin exists on.
	 *
	 * @return true if the pin's direction is in or inout
	 */
	public boolean isInput() {
		PinDirection direction = getDirection();
		return direction == PinDirection.IN || direction == PinDirection.INOUT;
	}

	/**
	 * Returns the site pins that either drive or are driven by this pin.
	 * <p>
	 * This method is especially helpful in determining which site pins a router
	 * should target to reach this BEL pin.  A set is returned since some BEL pins
	 * are reachable from multiple site pins.
	 * <p>
	 * The collection and the site pin objects are built upon each invocation of
	 * this method
	 *
	 * @return set of the site pins that either driver or are driven by this pin
	 * @see #getSitePinNames()
	 */
	public Set<SitePin> getSitePins() {
		if (template.getSitePins() == null)
			return Collections.emptySet();

		Site site = bel.getSite();
		SiteType siteType = getBel().getId().getSiteType();
		return template.getSitePins().stream()
				.map(sitePinName -> site.getPin(siteType, sitePinName))
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the names of the site pins that either drive or are driven by this
	 * pin.
	 * <p>
	 * This method is especially helpful in determining which site pins a router
	 * should target to reach this BEL pin.  A set is returned since some BEL pins
	 * are reachable from multiple site pins.
	 * <p>
	 * Unlike {@link #getSitePins()}, this method does not build any new objects.
	 *
	 * @return set of the site pins that either driver or are driven by this pin
	 * @see #getSitePins()
	 */
	public Set<String> getSitePinNames() {
		Set<String> sitePins = template.getSitePins();
		if (sitePins == null)
			return Collections.emptySet();
		return sitePins;
	}

	@Override
	public int hashCode() {
		return bel.hashCode() * 31 + template.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		final BelPin other = (BelPin) obj;
		return Objects.equals(this.bel, other.bel) &&
				Objects.equals(this.template, other.template);
	}

	@Override
	public String toString() {
		return "BelPin{" + bel.getSite().getName() +
				"/" + bel.getName() +
				"." + template.getName() +
				"}";
	}
}
