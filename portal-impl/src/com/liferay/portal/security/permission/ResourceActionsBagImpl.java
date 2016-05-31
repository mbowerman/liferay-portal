/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.security.permission;

import com.liferay.portal.kernel.security.permission.ResourceActionsBag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author László Csontos
 */
public class ResourceActionsBagImpl implements Cloneable, ResourceActionsBag {

	public ResourceActionsBagImpl(
		Set<String> resourceActions, Set<String> resourceGroupDefaultActions,
		Set<String> resourceGuestDefaultActions,
		Set<String> resourceGuestUnsupportedActions, Set<String> resources) {

		_resourceActions = Collections.unmodifiableSet(
			new HashSet<>(resourceActions));
		_resourceGroupDefaultActions = Collections.unmodifiableSet(
			new HashSet<>(resourceGroupDefaultActions));
		_resourceGuestDefaultActions = Collections.unmodifiableSet(
			new HashSet<>(resourceGuestDefaultActions));
		_resourceGuestUnsupportedActions = Collections.unmodifiableSet(
			new HashSet<>(resourceGuestUnsupportedActions));
		_resources = Collections.unmodifiableSet(
			new HashSet<>(resources));
	}

	public ResourceActionsBagImpl(ResourceActionsBag resourceActionsBag) {
		_resourceActions = resourceActionsBag.getResourceActions();
		_resourceGroupDefaultActions =
			resourceActionsBag.getResourceGroupDefaultActions();
		_resourceGuestDefaultActions =
			resourceActionsBag.getResourceGuestDefaultActions();
		_resourceGuestUnsupportedActions =
			resourceActionsBag.getResourceGuestUnsupportedActions();
		_resources = resourceActionsBag.getResources();
	}

	@Override
	public ResourceActionsBag clone() {
		return this;
	}

	@Override
	public Set<String> getResourceActions() {
		return _resourceActions;
	}

	@Override
	public Set<String> getResourceGroupDefaultActions() {
		return _resourceGroupDefaultActions;
	}

	@Override
	public Set<String> getResourceGuestDefaultActions() {
		return _resourceGuestDefaultActions;
	}

	@Override
	public Set<String> getResourceGuestUnsupportedActions() {
		return _resourceGuestUnsupportedActions;
	}

	@Override
	public Set<String> getResources() {
		return _resources;
	}

	private final Set<String> _resourceActions;
	private final Set<String> _resourceGroupDefaultActions;
	private final Set<String> _resourceGuestDefaultActions;
	private final Set<String> _resourceGuestUnsupportedActions;
	private final Set<String> _resources;

}