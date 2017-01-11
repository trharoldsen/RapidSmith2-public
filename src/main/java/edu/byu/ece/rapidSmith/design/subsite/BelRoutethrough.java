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
import static edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.util.Objects;

/**
 * <p>
 * Represents a routethrough passing through a {@link Bel}.
 * </p><p>
 * Some Bels can be configured such that they act as a wire allowing for greater
 * flexibility in the design.  In this case, the Bel is unused, but a connection from
 * an input pin to an output pin on the Bel is presented.  This class describes the
 * routethrough connection.
 * </p><p>
 * A BelRoutethrough is represented by an input {@link BelPin pin} and an output pin
 * on a Bel.  This class provides no validation that the provided pins can be used as a
 * routethrough.
 * </p>
 */
// TODO: Update this to include the sink cell pin that it leads to?
public class BelRoutethrough {

	/**Input pin of the BelRoutethrough (A1, A2, ... , A6) */
	private final BelPin inputPin;
	/**Input pin of the BelRoutethrough (O5, O6) */
	private final BelPin outputPin;

	/**
	 * Constructs a new BelRoutheThrough on the provided Bel with the provided input
	 * and output pins.
	 *
	 * @param inputPin the pin this routethrough enters through (A1 - A6 pins on a LUT typically)
	 * @param outputPin the pin this routethrough exits through (O5 or O6 pin on a LUT typically)
	 * @throws NullPointerException if any argument is null
	 * @throws DesignAssemblyException if inputPin and outputPin are on different bels
	 */public BelRoutethrough( BelPin inputPin, BelPin outputPin) {Objects.requireNonNull(bel);
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
	 * @return the Bel this routhethrough passes through
	 */
	public Bel getBel() {
		return this.inputPin.getBel();
	}

	/**
	 * Returns the {@link BelPin} through which this routethrough enters the Bel.
	 * @return the input pin on the Bel for this routethrough
	 */
	public BelPin getInputPin() {
		return this.inputPin;
	}

	/**
	 * Returns the {@link BelPin} through which this routethrough exits the Bel.
	 * @return the output pin on the Bel for this routethrough
	 */
	public BelPin getOutputPin() {
		return this.outputPin;
	}

	/**
	 * Returns the {@link Wire} connected to the output pin on this routethrough.
	 * @return the wire connected to the output pin on this routethrough
	 */
	public Wire getOutputWire() {
		return outputPin.getWire();
	}
}
