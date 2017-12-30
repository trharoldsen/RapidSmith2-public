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
	public SiteNode getNode() {
		return new SiteNode(getSite(), getSite().getNodeOfWire(template));
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
