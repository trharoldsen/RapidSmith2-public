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

/**
 * A property containing a key/value pair.  A {@link PropertyType property type}
 * can be specified to indicate the origin and use of the property.
 */
public class Property {
	private final Object key;
	private PropertyType type;
	private Object value;

	/**
	 * Constructs a new property with the following values.
	 * @param key the key/name for this property
	 * @param type the type of the property
	 * @param value the value of this property
	 */
	public Property(Object key, PropertyType type, Object value) {
		this.key = key;
		this.type = type;
		this.value = value;

	}

	/**
	 * Returns the key of this property.
	 * @return the key of this property
	 */
	public Object getKey() {
		return key;
	}

	/**
	 * Convenience method to get the key cast as a String.
	 * @return the key of this property as cast as a String
	 * @throws ClassCastException if the key is not a String
	 */
	public String getStringKey() {
		return (String) key;
	}

	/**
	 * Returns the {@link PropertyType property type}.  The type indicates the
	 * origin of the property and its use.
	 * @return the type of this property
	 */
	public PropertyType getType() {
		return type;
	}

	/**
	 * Sets the property type.  The type indicates the source of the property
	 * and its use.
	 * @param type the type for this property
	 */
	public void setType(PropertyType type) {
		this.type = type;
	}

	/**
	 * Returns the value of this property.
	 * @return the value of this property
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * Convenience method to get the value of the property cast as a String.
	 * @return the value of this property cast as a String
	 */
	public String getStringValue() {
		return (String) value;
	}

	/**
	 * Updates the value of this property.
	 * @param value the new value for this property
	 */
	public void setValue(Object value) {
		this.value = value;
	}

	/**
	 * Returns a new copy of this property with the same key/type/value.  The key
	 * and value are not copied.
	 * @return a new copy of this property
	 */
	public Property deepCopy() {
		return new Property(key, type, value);
	}

	@Override
	public String toString() {
		return "{ " + key + " -> " + value + " }";
	}
}
