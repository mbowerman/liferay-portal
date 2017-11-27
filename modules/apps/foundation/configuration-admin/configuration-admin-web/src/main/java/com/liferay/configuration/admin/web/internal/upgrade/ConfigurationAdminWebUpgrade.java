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

package com.liferay.configuration.admin.web.internal.upgrade;

import com.liferay.configuration.admin.web.internal.upgrade.v1_0_1.UpgradeConfigurationCleanerIgnore;
import com.liferay.configuration.admin.web.internal.util.ConfigurationModelRetriever;
import com.liferay.portal.kernel.upgrade.DummyUpgradeStep;
import com.liferay.portal.upgrade.registry.UpgradeStepRegistrator;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Michael Bowerman
 */
@Component(
	immediate = true,
	service = {ConfigurationAdminWebUpgrade.class, UpgradeStepRegistrator.class}
)
public class ConfigurationAdminWebUpgrade implements UpgradeStepRegistrator {

	@Override
	public void register(Registry registry) {
		registry.register(
			"com.liferay.configuration.admin.web", "1.0.0", "1.0.1",
			new UpgradeConfigurationCleanerIgnore(
				_configurationAdmin, _configurationModelRetriever));

		registry.register(
			"com.liferay.configuration.admin.web", "1.0.1", "1.0.0",
			new DummyUpgradeStep());
	}

	@Reference
	private ConfigurationAdmin _configurationAdmin;

	@Reference
	private ConfigurationModelRetriever _configurationModelRetriever;

}