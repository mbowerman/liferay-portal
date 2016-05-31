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

import com.liferay.portal.kernel.security.permission.ModelResourceActionsBag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author László Csontos
 */
public class ModelResourceActionsBagImpl
	extends ResourceActionsBagImpl implements ModelResourceActionsBag {

	public ModelResourceActionsBagImpl(
		Set<String> resourceActions, Set<String> resourceGroupDefaultActions,
		Set<String> resourceGuestDefaultActions,
		Set<String> resourceGuestUnsupportedActions, Set<String> resources,
		Set<String> resourceOwnerDefaultActions,
		Map<String, Double> resourceWeights) {

		super(
			resourceActions, resourceGroupDefaultActions,
			resourceGuestDefaultActions, resourceGuestUnsupportedActions,
			resources);

		_resourceOwnerDefaultActions = Collections.unmodifiableSet(
			new HashSet<>(resourceOwnerDefaultActions));
		_resourceWeights = Collections.unmodifiableMap(
			new HashMap<>(resourceWeights));
	}

	public ModelResourceActionsBagImpl(
		ModelResourceActionsBag modelResourceActionsBag) {

		super(modelResourceActionsBag);

		_resourceOwnerDefaultActions =
			modelResourceActionsBag.getResourceOwnerDefaultActions();
		_resourceWeights = modelResourceActionsBag.getResourceWeights();
	}

	@Override
	public ModelResourceActionsBag clone() {
		return new ModelResourceActionsBagImpl(this);
	}

	@Override
	public Set<String> getResourceOwnerDefaultActions() {
		return _resourceOwnerDefaultActions;
	}

	@Override
	public Map<String, Double> getResourceWeights() {
		return _resourceWeights;
	}

	private final Set<String> _resourceOwnerDefaultActions;
	private final Map<String, Double> _resourceWeights;

}