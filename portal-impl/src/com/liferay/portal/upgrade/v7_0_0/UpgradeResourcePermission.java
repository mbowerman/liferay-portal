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

package com.liferay.portal.upgrade.v7_0_0;

import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.LoggingTimer;

/**
 * @author Sampsa Sohlman
 */
public class UpgradeResourcePermission extends UpgradeProcess {

	protected void createIndex() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			runSQLTemplateString(
				"create index IX_D5F1E2A2 on ResourcePermission " +
					"(name[$COLUMN_LENGTH:255$])",
				false, false);
		}
	}

	@Override
	protected void doUpgrade() throws Exception {
		createIndex();

		upgradeResourcePermissions();
	}

	protected void upgradeResourcePermissions() throws Exception {
		runSQL(
			"update ResourcePermission set primKeyId = 0 + CAST_LONG(primKey)");

		runSQL(
			"update ResourcePermission set primKeyId = 0 where primKeyId is " +
				"null");

		runSQL(
			"update ResourcePermission set viewActionId = TRUE where 1 = " +
				"MOD(actionIds, 2)");

		runSQL(
			"update ResourcePermission set viewActionId = FALSE where " +
				"viewActionId is null");
	}

}