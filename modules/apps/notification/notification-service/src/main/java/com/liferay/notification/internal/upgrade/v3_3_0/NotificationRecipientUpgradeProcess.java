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

package com.liferay.notification.internal.upgrade.v3_3_0;

import com.liferay.notification.model.NotificationQueueEntry;
import com.liferay.notification.model.NotificationTemplate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * @author Michael Bowerman
 */
public class NotificationRecipientUpgradeProcess extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws Exception {
		if (hasColumn("NotificationRecipient", "className")) {
			alterTableDropColumn("NotificationRecipient", "className");
		}

		if (hasColumn("NotificationRecipient", "classNameId")) {
			return;
		}

		alterTableAddColumn("NotificationRecipient", "classNameId", "LONG");

		try (PreparedStatement preparedStatement1 = connection.prepareStatement(
				StringBundler.concat(
					"select notificationQueueEntryId, companyId, userId, ",
					"userName, createDate, modifiedDate FROM ",
					"NotificationQueueEntry"));
			ResultSet resultSet1 = preparedStatement1.executeQuery();
			PreparedStatement preparedStatement2 = connection.prepareStatement(
				StringBundler.concat(
					"select notificationTemplateId, companyId, userId, ",
					"userName, createDate, modifiedDate FROM ",
					"NotificationTemplate"));
			ResultSet resultSet2 = preparedStatement2.executeQuery();
			PreparedStatement preparedStatement3 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection,
					StringBundler.concat(
						"insert into NotificationRecipient (uuid_, ",
						"notificationRecipientId, companyId, userId, ",
						"userName, createDate, modifiedDate, classNameId, ",
						"classPK) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"))) {

			while (resultSet1.next()) {
				_insert(
					increment(), resultSet1.getLong("companyId"),
					resultSet1.getLong("userId"),
					resultSet1.getString("userName"),
					resultSet1.getTimestamp("createDate"),
					resultSet1.getTimestamp("modifiedDate"),
					PortalUtil.getClassNameId(NotificationQueueEntry.class),
					resultSet1.getLong("notificationQueueEntryId"),
					preparedStatement3);
			}

			while (resultSet2.next()) {
				_insert(
					increment(), resultSet2.getLong("companyId"),
					resultSet2.getLong("userId"),
					resultSet2.getString("userName"),
					resultSet2.getTimestamp("createDate"),
					resultSet2.getTimestamp("modifiedDate"),
					PortalUtil.getClassNameId(NotificationTemplate.class),
					resultSet2.getLong("notificationTemplateId"),
					preparedStatement3);
			}

			preparedStatement3.executeBatch();
		}

		if (_log.isInfoEnabled()) {
			_log.info(
				"Please manually re-create recipient data in your " +
					"Notifications portlet, as this data was erroneously " +
						"deleted during a previous upgrade process");
		}
	}

	private void _insert(
			long notificationRecipientId, long companyId, long userId,
			String userName, Timestamp createDate, Timestamp modifiedDate,
			long recipientClassNameId, long recipientClassPK,
			PreparedStatement preparedStatement3)
		throws SQLException {

		preparedStatement3.setString(1, PortalUUIDUtil.generate());
		preparedStatement3.setLong(2, notificationRecipientId);
		preparedStatement3.setLong(3, companyId);
		preparedStatement3.setLong(4, userId);
		preparedStatement3.setString(5, userName);
		preparedStatement3.setTimestamp(6, createDate);
		preparedStatement3.setTimestamp(7, modifiedDate);
		preparedStatement3.setLong(8, recipientClassNameId);
		preparedStatement3.setLong(9, recipientClassPK);

		preparedStatement3.addBatch();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		NotificationRecipientUpgradeProcess.class);

}