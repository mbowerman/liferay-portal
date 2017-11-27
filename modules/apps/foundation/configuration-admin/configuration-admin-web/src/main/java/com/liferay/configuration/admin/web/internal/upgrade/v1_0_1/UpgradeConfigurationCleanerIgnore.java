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

package com.liferay.configuration.admin.web.internal.upgrade.v1_0_1;

import com.liferay.configuration.admin.web.internal.model.ConfigurationModel;
import com.liferay.configuration.admin.web.internal.util.ConfigurationModelRetriever;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;

import java.io.File;

import java.net.URI;

import java.util.Dictionary;
import java.util.Map;

import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * @author Michael Bowerman
 */
public class UpgradeConfigurationCleanerIgnore extends UpgradeProcess {

	public UpgradeConfigurationCleanerIgnore(
		ConfigurationAdmin configurationAdmin,
		ConfigurationModelRetriever configurationModelRetriever) {

		_configurationAdmin = configurationAdmin;
		_configurationModelRetriever = configurationModelRetriever;
	}

	@Override
	protected void doUpgrade() throws Exception {
		Configuration[] configurations = _configurationAdmin.listConfigurations(
			null);

		for (Configuration configuration : configurations) {
			Dictionary<String, Object> dictionary =
				configuration.getProperties();

			String fileName = (String)dictionary.get(
				"felix.fileinstall.filename");

			if (fileName == null) {
				continue;
			}

			if (!_isFactory(dictionary)) {
				continue;
			}

			URI uri = new URI(fileName);

			File file = null;

			if (!uri.isOpaque()) {
				file = new File(uri);
			}

			if ((file == null) || !file.exists()) {
				dictionary.put("configuration.cleaner.ignore", "true");

				configuration.update(dictionary);
			}
		}
	}

	private boolean _isFactory(Dictionary<String, Object> dictionary) {
		String factoryPid = (String)dictionary.get(
			ConfigurationAdmin.SERVICE_FACTORYPID);

		if (factoryPid != null) {
			return _isFactory(factoryPid);
		}

		String pid = (String)dictionary.get(Constants.SERVICE_PID);

		if (pid != null) {
			return _isFactory(pid);
		}

		return false;
	}

	private boolean _isFactory(String pid) {
		Map<String, ConfigurationModel> configurationModels =
			_configurationModelRetriever.getConfigurationModels();

		ConfigurationModel configurationModel = configurationModels.get(pid);

		if (configurationModel == null) {
			return false;
		}

		return configurationModel.isFactory();
	}

	private final ConfigurationAdmin _configurationAdmin;
	private final ConfigurationModelRetriever _configurationModelRetriever;

}