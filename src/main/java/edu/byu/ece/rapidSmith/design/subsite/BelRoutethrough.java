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

package edu.byu.ece.rapidSmith.design.subsite;

import edu.byu.ece.rapidSmith.device.Bel;
import edu.byu.ece.rapidSmith.device.BelPin;
import edu.byu.ece.rapidSmith.device.Wire;
import edu.byu.ece.rapidSmith.interfaces.vivado.TincrCheckpoint;

import static edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.util.Objects;

/**
 * <p>
 * Represents a routethrough passing through a BEL.
 * </p><p>
 * Some BELs can be configured to act as a wire.  When configured this way, the
 * connection from the input pin to the output pin of the BEL is treated as a PIP and
 * the BEL is disabled.  These routethrough objects allow for greater routing
 * flexibility in the sites.
 * </p><p>
 * {@code BelRoutethrough} objects are used in the Tincr checkpoint files and have
 * properties that require special handling.  A {@code BelRoutethrough} object is
 * described by an input and an output {@code BelPin} that together form the source
 * and the sink of the routethrough.  These BEL pins should reside on the same BEL.
 * </p>
 * @see Bel
 * @see BelPin
 * @see TincrCheckpoint
 */
// TODO: Update this to include the sink cell pin that it leads to?
public class BelRoutethrough {

	/**Input pin of the BelRoutethrough (A1, A2, ... , A6) */
	private final BelPin inputPin;
	/**Input pin of the BelRoutethrough (O5, O6) */
	private final BelPin outputPin;

	/**
	 * Constructs a new {@code BelRoutethrough} instance representing a routethrough
	 * connecting {@code inputPin} to {@code outputPin}.
	 *
	 * @param inputPin the pin this routethrough enters through
	 *                    (typically the A1 - A6 pins on a LUT)
	 * @param outputPin the pin this routethrough exits through
	 *                     (typically the O5 or O6 pin on a LUT)
	 * @throws NullPointerException if a {@code null} argument is passed
	 * @throws DesignAssemblyException if the input and output pins are on different BELs
	 */public BelRoutethrough( BelPin inputPin, BelPin outputPin) {
		// Reject null objects
		Objects.requireNonNull(inputPin);
		Objects.requireNonNull(outputPin);

		if (!inputPin.getBel().equals(outputPin.getBel())) {
			throw new DesignAssemblyException("BelPins are not on the same Bel object!");
		}
		this.inputPin = inputPin;
		this.outputPin = outputPin;
	}

	/**
	 * Returns the {@link Bel} this routethrough passes through.
	 * @return the {@code Bel} this routhethrough passes through
	 */
	public Bel getBel() {
		return this.inputPin.getBel();
	}

	/**
	 * Returns the {@link BelPin} this routethrough enters through (typically the
	 * A1 - A6 pins on a LUT).
	 * @return the {@code BelPin} this routethrough enters through
	 */
	public BelPin getInputPin() {
		return this.inputPin;
	}

	/**
	 * Returns the {@link BelPin} this routethrough exits through (typically the
	 * O5 or O6 pins on a LUT).
	 * @return the {@code BelPin} this routethrough enters through
	 */
	public BelPin getOutputPin() {
		return this.outputPin;
	}

	/**
	 * Returns the {@link Wire} this routethrough exits on.  This is the wire
	 * the output pin connects to.
	 * @return the {@code BelPin} this routethrough enters through
	 * @see #getOutputPin()
	 */
	public Wire getOutputWire() {
		return outputPin.getWire();
	}
}
