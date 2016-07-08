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

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.UserTracker;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.UserTrackerLocalServiceUtil;
import com.liferay.portal.kernel.service.persistence.UserTrackerUtil;
import com.liferay.portal.kernel.servlet.PortalSessionContext;
import com.liferay.portal.util.PropsValues;

import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpSession;

/**
 * @author Charles May
 * @author Brian Wing Shun Chan
 */
public class LiveUsers {

	public static void addUserTrackers(Set<UserTracker> userTrackers) {
		_userTrackers.addAll(userTrackers);
	}

	public static void deleteGroup(long groupId) {
		_groupCounts.remove(groupId);
	}

	public static Set<UserTracker> getClusterNodeUsers(String clusterNodeId) {
		Set<UserTracker> clusterNodeUsers = new ConcurrentSkipListSet<>();

		for (UserTracker userTracker : _userTrackers) {
			if (clusterNodeId.equals(userTracker.getClusterNodeId())) {
				clusterNodeUsers.add(userTracker);
			}
		}

		return clusterNodeUsers;
	}

	public static Set<UserTracker> getCompanyUserTrackers(long companyId) {
		UserTracker lowerUserTracker = _getDummyUserTracker(companyId, 0, null);
		UserTracker higherUserTracker = _getDummyUserTracker(
			companyId, Long.MAX_VALUE, null);

		Set<UserTracker> userTrackers = _userTrackers.subSet(
			lowerUserTracker, higherUserTracker);

		return userTrackers;
	}

	public static int getGroupUsersCount(long groupId) {
		AtomicInteger count = _groupCounts.get(groupId);

		return count.intValue();
	}

	public static UserTracker getUserTracker(
		long companyId, long userId, String sessionId) {

		UserTracker equivalentUserTracker = _getDummyUserTracker(
			companyId, userId, sessionId);

		UserTracker userTracker = _userTrackers.ceiling(equivalentUserTracker);

		if ((userTracker != null) &&
			(userTracker.getCompanyId() == companyId) &&
			(userTracker.getUserId() == userId) &&
			sessionId.equals(userTracker.getSessionId())) {

			return userTracker;
		}

		return null;
	}

	public static Set<UserTracker> getUserUserTrackers(
		long companyId, long userId) {

		UserTracker lowerUserTracker = _getDummyUserTracker(
			companyId, userId, "");

		UserTracker higherUserTracker = _getDummyUserTracker(
			companyId, userId, null);

		Set<UserTracker> userTrackers = _userTrackers.subSet(
			lowerUserTracker, higherUserTracker);

		return userTrackers;
	}

	public static void joinGroup(long groupId) {
		AtomicInteger count = _groupCounts.get(groupId);

		if (count == null) {
			count = new AtomicInteger();

			_groupCounts.put(groupId, count);
		}

		count.incrementAndGet();
	}

	public static void joinGroup(long groupId, int numUsers) {
		AtomicInteger count = _groupCounts.get(groupId);

		if (count == null) {
			count = new AtomicInteger();

			_groupCounts.put(groupId, count);
		}

		count.addAndGet(numUsers);
	}

	public static void leaveGroup(long groupId) {
		AtomicInteger count = _groupCounts.get(groupId);

		int newCount = count.decrementAndGet();

		if (newCount <= 0) {
			_groupCounts.remove(groupId);
		}
	}

	public static void leaveGroup(long groupId, int numUsers) {
		AtomicInteger count = _groupCounts.get(groupId);

		int newCount = count.addAndGet(-numUsers);

		if (newCount <= 0) {
			_groupCounts.remove(groupId);
		}
	}

	public static void removeClusterNodeUsers(String clusterNodeId) {
		Iterator<UserTracker> iterator = _userTrackers.iterator();

		while (iterator.hasNext()) {
			UserTracker userTracker = iterator.next();

			if (clusterNodeId.equals(userTracker.getClusterNodeId())) {
				iterator.remove();
			}
		}
	}

	public static void signIn(
		String clusterNodeId, long companyId, long userId, String sessionId,
		String remoteAddr, String remoteHost, String userAgent) {

		_updateGroupStatus(companyId, userId, true);

		UserTracker userTracker = getUserTracker(companyId, userId, sessionId);

		if ((userTracker == null) &&
			PropsValues.SESSION_TRACKER_MEMORY_ENABLED) {

			userTracker = UserTrackerUtil.create(
				CounterLocalServiceUtil.increment(UserTracker.class.getName()));

			userTracker.setClusterNodeId(clusterNodeId);
			userTracker.setCompanyId(companyId);
			userTracker.setUserId(userId);
			userTracker.setModifiedDate(new Date());
			userTracker.setSessionId(sessionId);
			userTracker.setRemoteAddr(remoteAddr);
			userTracker.setRemoteHost(remoteHost);
			userTracker.setUserAgent(userAgent);

			_userTrackers.add(userTracker);
		}
	}

	public static void signOut(long companyId, long userId, String sessionId) {
		Set<UserTracker> userTrackers = getUserUserTrackers(companyId, userId);

		if ((userTrackers == null) || (userTrackers.size() <= 1)) {
			_updateGroupStatus(companyId, userId, false);
		}

		UserTracker userTracker = getUserTracker(companyId, userId, sessionId);

		if (userTracker == null) {
			return;
		}

		_userTrackers.remove(userTracker);

		UserTrackerLocalServiceUtil.updateUserTracker(userTracker);

		HttpSession session = PortalSessionContext.get(sessionId);

		if (session != null) {
			session.invalidate();
		}
	}

	private static UserTracker _getDummyUserTracker(
		long companyId, long userId, String sessionId) {

		UserTracker userTracker = UserTrackerUtil.create(0);

		userTracker.setCompanyId(companyId);
		userTracker.setUserId(userId);
		userTracker.setSessionId(sessionId);

		return userTracker;
	}

	private static void _updateGroupStatus(
		long companyId, long userId, boolean signedIn) {

		LinkedHashMap<String, Object> groupParams = new LinkedHashMap<>();

		groupParams.put("usersGroups", userId);

		List<Group> groups = GroupLocalServiceUtil.search(
			companyId, null, null, groupParams, QueryUtil.ALL_POS,
			QueryUtil.ALL_POS);

		for (Group group : groups) {
			if (signedIn) {
				joinGroup(group.getGroupId());
			}
			else {
				leaveGroup(group.getGroupId());
			}
		}
	}

	private static final Map<Long, AtomicInteger> _groupCounts =
		new ConcurrentHashMap<>();
	private static final ConcurrentSkipListSet<UserTracker> _userTrackers =
		new ConcurrentSkipListSet<>(new UserTrackerComparator());

	private static class UserTrackerComparator
		implements Comparator<UserTracker> {

		@Override
		public int compare(UserTracker ut1, UserTracker ut2) {
			if (ut1.getCompanyId() < ut2.getCompanyId()) {
				return -1;
			}

			if (ut1.getCompanyId() > ut2.getCompanyId()) {
				return 1;
			}

			if (ut1.getUserId() < ut2.getUserId()) {
				return -1;
			}

			if (ut1.getUserId() > ut2.getUserId()) {
				return 1;
			}

			if ((ut1.getSessionId() == null) && (ut2.getSessionId() == null)) {
				return 0;
			}

			if (ut1.getSessionId() == null) {
				return 1;
			}

			if (ut2.getSessionId() == null) {
				return -1;
			}

			return ut1.getSessionId().compareTo(ut2.getSessionId());
		}

	}

}