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

package com.liferay.portal.liveusers;

import com.liferay.portal.kernel.model.UserTracker;

import java.util.Set;

/**
 * @author Charles May
 * @author Brian Wing Shun Chan
 */
public class LiveUsers {

	public static void addUserTrackers(Set<UserTracker> userTrackers) {
	}

	public static void deleteGroup(long groupId) {
	}

	public static Set<UserTracker> getClusterNodeUsers(String clusterNodeId) {
		return null;
	}

	public static Set<UserTracker> getCompanyUserTrackers(long companyId) {
		return null;
	}

	public static int getGroupUsersCount(long groupId) {
		return 0;
	}

	public static UserTracker getUserTracker(
		long companyId, long userId, String sessionId) {

		return null;
	}

	public static Set<UserTracker> getUserUserTrackers(
		long companyId, long userId) {

		return null;
	}

	public static void joinGroup(long groupId) {
	}

	public static void joinGroup(long groupId, int numUsers) {
	}

	public static void leaveGroup(long groupId) {
	}

	public static void leaveGroup(long groupId, int numUsers) {
	}

	public static void removeClusterNodeUsers(String clusterNodeId) {
	}

	public static void signIn(
		String clusterNodeId, long companyId, long userId, String sessionId,
		String remoteAddr, String remoteHost, String userAgent) {
	}

	public static void signOut(long companyId, long userId, String sessionId) {
	}

}