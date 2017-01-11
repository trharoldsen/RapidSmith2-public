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

import edu.byu.ece.rapidSmith.device.SiteType;

import java.util.Objects;

/**
 * Control set properties for the entire site.  Any site properties must be
 * configured uniformly for all cells in a site.
 */
public class SiteProperty {
	private final SiteType siteType;
	private final String propertyName;

	/**
	 * Creates a new SiteProperty.
	 * @param siteType the type of the site this property exists for
	 * @param propertyName the name of the property
	 */
	public SiteProperty(SiteType siteType, String propertyName) {
		this.siteType = siteType;
		this.propertyName = propertyName;
	}

	/**
	 * Returns the SiteType this property exists for
	 * @return the SiteType this property exists for
	 */
	public SiteType getSiteType() {
		return siteType;
	}

	/**
	 * Returns the name of the property.
	 * @return the name of the property
	 */
	public String getPropertyName() {
		return propertyName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SiteProperty that = (SiteProperty) o;
		return siteType == that.siteType &&
				Objects.equals(propertyName, that.propertyName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(siteType, propertyName);
	}

	@Override
	public String toString() {
		return "SiteProperty{" +
				"siteType=" + siteType +
				", propertyName='" + propertyName + '\'' +
				'}';
	}
}
