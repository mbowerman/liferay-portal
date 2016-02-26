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

package com.liferay.calendar.service.impl;

import com.google.ical.values.DateValue;

import com.liferay.calendar.exception.CalendarBookingDurationException;
import com.liferay.calendar.exception.CalendarBookingRecurrenceException;
import com.liferay.calendar.exporter.CalendarDataFormat;
import com.liferay.calendar.exporter.CalendarDataHandler;
import com.liferay.calendar.exporter.CalendarDataHandlerFactory;
import com.liferay.calendar.model.Calendar;
import com.liferay.calendar.model.CalendarBooking;
import com.liferay.calendar.model.CalendarBookingConstants;
import com.liferay.calendar.notification.NotificationTemplateType;
import com.liferay.calendar.notification.NotificationType;
import com.liferay.calendar.notification.impl.NotificationUtil;
import com.liferay.calendar.recurrence.Frequency;
import com.liferay.calendar.recurrence.PositionalWeekday;
import com.liferay.calendar.recurrence.Recurrence;
import com.liferay.calendar.recurrence.RecurrenceSerializer;
import com.liferay.calendar.recurrence.Weekday;
import com.liferay.calendar.service.base.CalendarBookingLocalServiceBaseImpl;
import com.liferay.calendar.service.configuration.CalendarServiceConfigurationValues;
import com.liferay.calendar.social.CalendarActivityKeys;
import com.liferay.calendar.util.CalendarBookingIterator;
import com.liferay.calendar.util.JCalendarUtil;
import com.liferay.calendar.util.RecurrenceUtil;
import com.liferay.calendar.workflow.CalendarBookingWorkflowConstants;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.sanitizer.Sanitizer;
import com.liferay.portal.kernel.sanitizer.SanitizerUtil;
import com.liferay.portal.kernel.search.Indexable;
import com.liferay.portal.kernel.search.IndexableType;
import com.liferay.portal.kernel.systemevent.SystemEvent;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowHandlerRegistryUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.SystemEventConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.model.AssetLinkConstants;
import com.liferay.social.kernel.model.SocialActivityConstants;
import com.liferay.trash.kernel.model.TrashEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * @author Eduardo Lundgren
 * @author Fabio Pezzutto
 * @author Marcellus Tavares
 * @author Pier Paolo Ramon
 */
public class CalendarBookingLocalServiceImpl
	extends CalendarBookingLocalServiceBaseImpl {

	@Override
	public CalendarBooking addCalendarBooking(
			long userId, long calendarId, long[] childCalendarIds,
			long parentCalendarBookingId, long recurringCalendarBookingId,
			Map<Locale, String> titleMap, Map<Locale, String> descriptionMap,
			String location, long startTime, long endTime, boolean allDay,
			String recurrence, String masterRecurrence, long firstReminder,
			String firstReminderType, long secondReminder,
			String secondReminderType, ServiceContext serviceContext)
		throws PortalException {

		// Calendar booking

		User user = userPersistence.findByPrimaryKey(userId);
		Calendar calendar = calendarPersistence.findByPrimaryKey(calendarId);

		long calendarBookingId = counterLocalService.increment();

		for (Locale locale : descriptionMap.keySet()) {
			String sanitizedDescription = SanitizerUtil.sanitize(
				calendar.getCompanyId(), calendar.getGroupId(), userId,
				CalendarBooking.class.getName(), calendarBookingId,
				ContentTypes.TEXT_HTML, Sanitizer.MODE_ALL,
				descriptionMap.get(locale), null);

			descriptionMap.put(locale, sanitizedDescription);
		}

		java.util.Calendar startTimeJCalendar = JCalendarUtil.getJCalendar(
			startTime);
		java.util.Calendar endTimeJCalendar = JCalendarUtil.getJCalendar(
			endTime);

		if (allDay) {
			startTimeJCalendar = JCalendarUtil.toMidnightJCalendar(
				startTimeJCalendar);
			endTimeJCalendar = JCalendarUtil.toLastHourJCalendar(
				endTimeJCalendar);
		}

		if (firstReminder < secondReminder) {
			long originalSecondReminder = secondReminder;

			secondReminder = firstReminder;
			firstReminder = originalSecondReminder;
		}

		Date now = new Date();

		validate(startTimeJCalendar, endTimeJCalendar, recurrence);

		CalendarBooking calendarBooking = calendarBookingPersistence.create(
			calendarBookingId);

		calendarBooking.setGroupId(calendar.getGroupId());
		calendarBooking.setCompanyId(user.getCompanyId());
		calendarBooking.setUserId(user.getUserId());
		calendarBooking.setUserName(user.getFullName());
		calendarBooking.setCreateDate(serviceContext.getCreateDate(now));
		calendarBooking.setModifiedDate(serviceContext.getModifiedDate(now));
		calendarBooking.setCalendarId(calendarId);
		calendarBooking.setCalendarResourceId(calendar.getCalendarResourceId());

		if (parentCalendarBookingId > 0) {
			calendarBooking.setParentCalendarBookingId(parentCalendarBookingId);
		}
		else {
			calendarBooking.setParentCalendarBookingId(calendarBookingId);
		}

		if (recurringCalendarBookingId > 0) {
			calendarBooking.setRecurringCalendarBookingId(
				recurringCalendarBookingId);
		}
		else {
			calendarBooking.setRecurringCalendarBookingId(calendarBookingId);
		}

		String vEventUid = (String)serviceContext.getAttribute("vEventUid");

		if (vEventUid == null) {
			vEventUid = PortalUUIDUtil.generate();
		}

		calendarBooking.setVEventUid(vEventUid);
		calendarBooking.setTitleMap(titleMap, serviceContext.getLocale());
		calendarBooking.setDescriptionMap(descriptionMap);
		calendarBooking.setLocation(location);
		calendarBooking.setStartTime(startTimeJCalendar.getTimeInMillis());
		calendarBooking.setEndTime(endTimeJCalendar.getTimeInMillis());
		calendarBooking.setAllDay(allDay);
		calendarBooking.setRecurrence(recurrence);
		calendarBooking.setMasterRecurrence(masterRecurrence);
		calendarBooking.setFirstReminder(firstReminder);
		calendarBooking.setFirstReminderType(firstReminderType);
		calendarBooking.setSecondReminder(secondReminder);
		calendarBooking.setSecondReminderType(secondReminderType);
		calendarBooking.setExpandoBridgeAttributes(serviceContext);

		if (calendarBooking.isMasterBooking()) {
			calendarBooking.setStatus(
				CalendarBookingWorkflowConstants.STATUS_DRAFT);
		}
		else {
			calendarBooking.setStatus(
				CalendarBookingWorkflowConstants.STATUS_MASTER_PENDING);
		}

		calendarBooking.setStatusDate(serviceContext.getModifiedDate(now));

		calendarBookingPersistence.update(calendarBooking);

		addChildCalendarBookings(
			calendarBooking, childCalendarIds, serviceContext);

		// Resources

		resourceLocalService.addModelResources(calendarBooking, serviceContext);

		// Asset

		updateAsset(
			userId, calendarBooking, serviceContext.getAssetCategoryIds(),
			serviceContext.getAssetTagNames(),
			serviceContext.getAssetLinkEntryIds(),
			serviceContext.getAssetPriority());

		// Social

		socialActivityLocalService.addActivity(
			userId, calendarBooking.getGroupId(),
			CalendarBooking.class.getName(), calendarBookingId,
			CalendarActivityKeys.ADD_CALENDAR_BOOKING,
			getExtraDataJSON(calendarBooking), 0);

		// Notifications

		sendNotification(
			calendarBooking, NotificationTemplateType.INVITE, serviceContext);

		// Workflow

		if (calendarBooking.isMasterBooking()) {
			WorkflowHandlerRegistryUtil.startWorkflowInstance(
				calendarBooking.getCompanyId(), calendarBooking.getGroupId(),
				userId, CalendarBooking.class.getName(),
				calendarBooking.getCalendarBookingId(), calendarBooking,
				serviceContext);
		}

		return calendarBooking;
	}

	@Override
	public void checkCalendarBookings() throws PortalException {
		Date now = new Date();

		List<CalendarBooking> calendarBookings =
			calendarBookingFinder.findByFutureReminders(now.getTime());

		long endTime = now.getTime() + Time.MONTH;

		calendarBookings = RecurrenceUtil.expandCalendarBookings(
			calendarBookings, now.getTime(), endTime, 1);

		for (CalendarBooking calendarBooking : calendarBookings) {
			try {
				Company company = companyPersistence.findByPrimaryKey(
					calendarBooking.getCompanyId());

				if (company.isActive()) {
					NotificationUtil.notifyCalendarBookingReminders(
						calendarBooking, now.getTime());
				}
			}
			catch (PortalException pe) {
				throw pe;
			}
			catch (SystemException se) {
				throw se;
			}
			catch (Exception e) {
				throw new SystemException(e);
			}
		}
	}

	/**
	 * @deprecated As of 7.0.0, replaced by {@link #deleteCalendarBooking(
	 *             CalendarBooking, boolean)}
	 */
	@Deprecated
	@Override
	public CalendarBooking deleteCalendarBooking(
			CalendarBooking calendarBooking)
		throws PortalException {

		return deleteCalendarBooking(calendarBooking, true);
	}

	@Indexable(type = IndexableType.DELETE)
	@Override
	@SystemEvent(
		action = SystemEventConstants.ACTION_SKIP,
		type = SystemEventConstants.TYPE_DELETE
	)
	public CalendarBooking deleteCalendarBooking(
			CalendarBooking calendarBooking, boolean allRecurringInstances)
		throws PortalException {

		long calendarBookingId = calendarBooking.getCalendarBookingId();

		Set<CalendarBooking> relatedCalendarBookings = new HashSet<>();

		relatedCalendarBookings.addAll(
			getChildCalendarBookings(calendarBookingId));

		if (allRecurringInstances) {
			relatedCalendarBookings.addAll(
				getRelatedRecurringCalendarBookings(calendarBookingId));
		}
		else {
			relatedCalendarBookings.add(calendarBooking);
		}

		for (CalendarBooking relatedCalendarBooking : relatedCalendarBookings) {

			// Calendar booking

			calendarBookingPersistence.remove(relatedCalendarBooking);

			// Resources

			resourceLocalService.deleteResource(
				relatedCalendarBooking, ResourceConstants.SCOPE_INDIVIDUAL);

			// Subscriptions

			subscriptionLocalService.deleteSubscriptions(
				relatedCalendarBooking.getCompanyId(),
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());

			// Asset

			assetEntryLocalService.deleteEntry(
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());

			// Message boards

			mbMessageLocalService.deleteDiscussionMessages(
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());

			// Ratings

			ratingsStatsLocalService.deleteStats(
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());

			// Trash

			trashEntryLocalService.deleteEntry(
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());

			// Workflow

			workflowInstanceLinkLocalService.deleteWorkflowInstanceLinks(
				relatedCalendarBooking.getCompanyId(),
				relatedCalendarBooking.getGroupId(),
				CalendarBooking.class.getName(),
				relatedCalendarBooking.getCalendarBookingId());
		}

		return calendarBooking;
	}

	@Override
	public CalendarBooking deleteCalendarBooking(long calendarBookingId)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		calendarBookingLocalService.deleteCalendarBooking(
			calendarBooking, true);

		return calendarBooking;
	}

	@Override
	public void deleteCalendarBookingInstance(
			CalendarBooking calendarBooking, int instanceIndex,
			boolean allFollowing)
		throws PortalException {

		deleteCalendarBookingInstance(
			calendarBooking, instanceIndex, allFollowing, true);
	}

	@Override
	public void deleteCalendarBookingInstance(
			CalendarBooking calendarBooking, int instanceIndex,
			boolean allFollowing, boolean updateMasterRecurrence)
		throws PortalException {

		CalendarBooking calendarBookingInstance =
			RecurrenceUtil.getCalendarBookingInstance(
				calendarBooking, instanceIndex);

		deleteCalendarBookingInstance(
			calendarBooking, calendarBookingInstance.getStartTime(),
			allFollowing, updateMasterRecurrence);
	}

	@Override
	public void deleteCalendarBookingInstance(
			CalendarBooking calendarBooking, long startTime,
			boolean allFollowing)
		throws PortalException {

		deleteCalendarBookingInstance(
			calendarBooking, startTime, allFollowing, true);
	}

	@Override
	public void deleteCalendarBookingInstance(
			CalendarBooking calendarBooking, long startTime,
			boolean allFollowing, boolean updateMasterRecurrence)
		throws PortalException {

		Date now = new Date();

		java.util.Calendar startTimeJCalendar = JCalendarUtil.getJCalendar(
			startTime);

		Recurrence recurrenceObj = calendarBooking.getRecurrenceObj();
		Recurrence masterRecurrenceObj =
			calendarBooking.getMasterRecurrenceObj();

		if (allFollowing) {
			if (updateMasterRecurrence) {
				List<CalendarBooking> recurringCalendarBookings =
					getRelatedRecurringCalendarBookings(
						calendarBooking.getCalendarBookingId());

				for (CalendarBooking recurringCalendarBooking :
						recurringCalendarBookings) {

					if (recurringCalendarBooking.getStartTime() >= startTime) {
						deleteCalendarBooking(recurringCalendarBooking, false);
					}
				}
			}

			if (recurrenceObj.getCount() > 0) {
				recurrenceObj.setCount(0);
			}

			if ((masterRecurrenceObj.getCount() > 0) &&
				updateMasterRecurrence) {

				masterRecurrenceObj.setCount(0);
			}

			startTimeJCalendar.add(java.util.Calendar.DATE, -1);

			recurrenceObj.setUntilJCalendar(startTimeJCalendar);

			List<java.util.Calendar> exceptionJCalendars = new ArrayList<>(
				recurrenceObj.getExceptionJCalendars());

			for (java.util.Calendar exceptionJCalendar : exceptionJCalendars) {
				if (exceptionJCalendar.after(startTimeJCalendar)) {
					recurrenceObj.removeExceptionDate(exceptionJCalendar);
				}
			}

			if (updateMasterRecurrence) {
				masterRecurrenceObj.setUntilJCalendar(startTimeJCalendar);

				List<java.util.Calendar> masterExceptionJCalendars =
					new ArrayList<>(
						masterRecurrenceObj.getExceptionJCalendars());

				for (java.util.Calendar masterExceptionJCalendar :
						masterExceptionJCalendars) {

					if (masterExceptionJCalendar.after(startTimeJCalendar)) {
						masterRecurrenceObj.removeExceptionDate(
							masterExceptionJCalendar);
					}
				}
			}
		}
		else {
			CalendarBooking calendarBookingInstance =
				RecurrenceUtil.getCalendarBookingInstance(calendarBooking, 1);

			if ((calendarBookingInstance == null) || (recurrenceObj == null)) {
				if ((recurrenceObj == null) && updateMasterRecurrence) {
					masterRecurrenceObj.addExceptionDate(startTimeJCalendar);

					String masterRecurrence = RecurrenceSerializer.serialize(
						masterRecurrenceObj);

					updateChildCalendarBookings(
						calendarBooking, now, null, masterRecurrence);
				}

				calendarBookingLocalService.deleteCalendarBooking(
					calendarBooking, false);

				return;
			}

			recurrenceObj.addExceptionDate(startTimeJCalendar);

			if (updateMasterRecurrence) {
				masterRecurrenceObj.addExceptionDate(startTimeJCalendar);
			}
		}

		String recurrence = RecurrenceSerializer.serialize(recurrenceObj);
		String masterRecurrence = RecurrenceSerializer.serialize(
			masterRecurrenceObj);

		updateChildCalendarBookings(
			calendarBooking, now, recurrence, masterRecurrence);
	}

	@Override
	public void deleteCalendarBookingInstance(
			long calendarBookingId, long startTime, boolean allFollowing)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		deleteCalendarBookingInstance(calendarBooking, startTime, allFollowing);
	}

	@Override
	public void deleteCalendarBookings(long calendarId) throws PortalException {
		List<CalendarBooking> calendarBookings =
			calendarBookingPersistence.findByCalendarId(calendarId);

		for (CalendarBooking calendarBooking : calendarBookings) {
			calendarBookingLocalService.deleteCalendarBooking(calendarBooking);
		}
	}

	@Override
	public String exportCalendarBooking(long calendarBookingId, String type)
		throws Exception {

		CalendarDataFormat calendarDataFormat = CalendarDataFormat.parse(type);

		CalendarDataHandler calendarDataHandler =
			CalendarDataHandlerFactory.getCalendarDataHandler(
				calendarDataFormat);

		return calendarDataHandler.exportCalendarBooking(calendarBookingId);
	}

	@Override
	public CalendarBooking fetchCalendarBooking(
		long calendarId, String vEventUid) {

		return calendarBookingPersistence.fetchByC_V(calendarId, vEventUid);
	}

	@Override
	public CalendarBooking fetchCalendarBooking(String uuid, long groupId) {
		return calendarBookingPersistence.fetchByUUID_G(uuid, groupId);
	}

	@Override
	public CalendarBooking getCalendarBooking(long calendarBookingId)
		throws PortalException {

		return calendarBookingPersistence.findByPrimaryKey(calendarBookingId);
	}

	@Override
	public CalendarBooking getCalendarBooking(
			long calendarId, long parentCalendarBookingId)
		throws PortalException {

		return calendarBookingPersistence.findByC_P(
			calendarId, parentCalendarBookingId);
	}

	@Override
	public CalendarBooking getCalendarBookingInstance(
			long calendarBookingId, int instanceIndex)
		throws PortalException {

		CalendarBooking calendarBooking = getCalendarBooking(calendarBookingId);

		return RecurrenceUtil.getCalendarBookingInstance(
			calendarBooking, instanceIndex);
	}

	@Override
	public List<CalendarBooking> getCalendarBookings(long calendarId) {
		return calendarBookingPersistence.findByCalendarId(calendarId);
	}

	@Override
	public List<CalendarBooking> getCalendarBookings(
		long calendarId, int[] statuses) {

		return calendarBookingPersistence.findByC_S(calendarId, statuses);
	}

	@Override
	public List<CalendarBooking> getCalendarBookings(
		long calendarId, long startTime, long endTime) {

		return getCalendarBookings(
			calendarId, startTime, endTime, QueryUtil.ALL_POS);
	}

	@Override
	public List<CalendarBooking> getCalendarBookings(
		long calendarId, long startTime, long endTime, int max) {

		DynamicQuery dynamicQuery = DynamicQueryFactoryUtil.forClass(
			CalendarBooking.class, getClassLoader());

		Property property = PropertyFactoryUtil.forName("calendarId");

		dynamicQuery.add(property.eq(calendarId));

		if (startTime >= 0) {
			Property propertyStartTime = PropertyFactoryUtil.forName(
				"startTime");

			dynamicQuery.add(propertyStartTime.gt(startTime));
		}

		if (endTime >= 0) {
			Property propertyEndTime = PropertyFactoryUtil.forName("endTime");

			dynamicQuery.add(propertyEndTime.lt(endTime));
		}

		if (max > 0) {
			dynamicQuery.setLimit(0, max);
		}

		return dynamicQuery(dynamicQuery);
	}

	@Override
	public int getCalendarBookingsCount(
		long calendarId, long parentCalendarBookingId) {

		return calendarBookingPersistence.countByC_P(
			calendarId, parentCalendarBookingId);
	}

	@Override
	public CalendarBooking getCalendarBookingWithDate(
		List<CalendarBooking> calendarBookings, DateValue dateValue) {

		for (CalendarBooking calendarBooking : calendarBookings) {
			if (hasDateValue(calendarBooking, dateValue)) {
				return calendarBooking;
			}
		}

		return null;
	}

	@Override
	public List<CalendarBooking> getChildCalendarBookings(
		long calendarBookingId) {

		return calendarBookingPersistence.findByParentCalendarBookingId(
			calendarBookingId);
	}

	@Override
	public List<CalendarBooking> getChildCalendarBookings(
		long parentCalendarBookingId, int status) {

		return calendarBookingPersistence.findByP_S(
			parentCalendarBookingId, status);
	}

	@Override
	public long[] getChildCalendarIds(long calendarBookingId, long calendarId)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		List<CalendarBooking> childCalendarBookings =
			calendarBookingPersistence.findByParentCalendarBookingId(
				calendarBookingId);

		long[] childCalendarIds = new long[childCalendarBookings.size()];

		for (int i = 0; i < childCalendarIds.length; i++) {
			CalendarBooking childCalendarBooking = childCalendarBookings.get(i);

			if (childCalendarBooking.getCalendarId() ==
					calendarBooking.getCalendarId()) {

				childCalendarIds[i] = calendarId;
			}
			else {
				childCalendarIds[i] = childCalendarBooking.getCalendarId();
			}
		}

		return childCalendarIds;
	}

	@Override
	public CalendarBooking getEarliestCalendarBooking(
		List<CalendarBooking> calendarBookings) {

		CalendarBooking earliestCalendarBooking = calendarBookings.get(0);

		for (CalendarBooking calendarBooking : calendarBookings) {
			if (earliestCalendarBooking.getStartTime() >
					calendarBooking.getStartTime()) {

				earliestCalendarBooking = calendarBooking;
			}
		}

		return earliestCalendarBooking;
	}

	@Override
	public long getEarliestStartTime(List<CalendarBooking> calendarBookings) {
		long startTime = calendarBookings.get(0).getStartTime();

		for (CalendarBooking calendarBooking : calendarBookings) {
			if (startTime > calendarBooking.getStartTime()) {
				startTime = calendarBooking.getStartTime();
			}
		}

		return startTime;
	}

	@Override
	public CalendarBooking getMasterRecurringCalendarBooking(
			CalendarBooking calendarBooking)
		throws PortalException {

		long recurringCalendarBookingId =
			calendarBooking.getRecurringCalendarBookingId();

		if (calendarBooking.getCalendarBookingId() ==
				recurringCalendarBookingId) {

			return calendarBooking;
		}

		return calendarBookingPersistence.findByPrimaryKey(
			recurringCalendarBookingId);
	}

	@Override
	public List<CalendarBooking> getRelatedRecurringCalendarBookings(
			long calendarBookingId)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		long recurringCalendarBookingId =
			calendarBooking.getRecurringCalendarBookingId();

		return calendarBookingPersistence.findByRecurringCalendarBookingId(
			recurringCalendarBookingId);
	}

	@Override
	public boolean hasValidStartTime(CalendarBooking calendarBooking) {
		long startTime = calendarBooking.getStartTime();

		java.util.Calendar startJCalendar = JCalendarUtil.getJCalendar(
			startTime);

		Recurrence masterRecurrenceObj =
			calendarBooking.getMasterRecurrenceObj();

		if (masterRecurrenceObj == null) {
			return true;
		}

		return hasCalendarDate(
			masterRecurrenceObj, startJCalendar, startJCalendar,
			startJCalendar);
	}

	@Override
	public CalendarBooking moveCalendarBookingToTrash(
			long userId, CalendarBooking calendarBooking)
		throws PortalException {

		// Calendar booking

		if (!calendarBooking.isMasterBooking()) {
			return calendarBooking;
		}

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setUserId(userId);

		List<CalendarBooking> recurringCalendarBookings =
			getRelatedRecurringCalendarBookings(
				calendarBooking.getCalendarBookingId());

		for (CalendarBooking recurringCalendarBooking :
				recurringCalendarBookings) {

			calendarBookingLocalService.updateStatus(
				userId, recurringCalendarBooking,
				CalendarBookingWorkflowConstants.STATUS_IN_TRASH,
				serviceContext);

			// Social

			socialActivityCounterLocalService.disableActivityCounters(
				CalendarBooking.class.getName(),
				recurringCalendarBooking.getCalendarBookingId());

			socialActivityLocalService.addActivity(
				userId, recurringCalendarBooking.getGroupId(),
				CalendarBooking.class.getName(),
				recurringCalendarBooking.getCalendarBookingId(),
				SocialActivityConstants.TYPE_MOVE_TO_TRASH,
				getExtraDataJSON(calendarBooking), 0);

			// Workflow

			workflowInstanceLinkLocalService.deleteWorkflowInstanceLinks(
				recurringCalendarBooking.getCompanyId(),
				recurringCalendarBooking.getGroupId(),
				CalendarBooking.class.getName(),
				recurringCalendarBooking.getCalendarBookingId());
		}

		return calendarBooking;
	}

	@Override
	public CalendarBooking moveCalendarBookingToTrash(
			long userId, long calendarBookingId)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		return moveCalendarBookingToTrash(userId, calendarBooking);
	}

	@Override
	public CalendarBooking restoreCalendarBookingFromTrash(
			long userId, long calendarBookingId)
		throws PortalException {

		// Calendar booking

		CalendarBooking calendarBooking = getCalendarBooking(calendarBookingId);

		if (!calendarBooking.isMasterBooking()) {
			return calendarBooking;
		}

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setUserId(userId);

		TrashEntry trashEntry = trashEntryLocalService.getEntry(
			CalendarBooking.class.getName(), calendarBookingId);

		calendarBookingLocalService.updateStatus(
			userId, calendarBookingId, trashEntry.getStatus(), serviceContext);

		// Social

		socialActivityCounterLocalService.enableActivityCounters(
			CalendarBooking.class.getName(), calendarBookingId);

		socialActivityLocalService.addActivity(
			userId, calendarBooking.getGroupId(),
			CalendarBooking.class.getName(), calendarBookingId,
			SocialActivityConstants.TYPE_RESTORE_FROM_TRASH,
			getExtraDataJSON(calendarBooking), 0);

		// Workflow

		WorkflowHandlerRegistryUtil.startWorkflowInstance(
			calendarBooking.getCompanyId(), calendarBooking.getGroupId(),
			userId, CalendarBooking.class.getName(),
			calendarBooking.getCalendarBookingId(), calendarBooking,
			serviceContext);

		if (calendarBooking.isMasterRecurringBooking()) {
			List<CalendarBooking> relatedCalendarBookings =
				getRelatedRecurringCalendarBookings(calendarBookingId);

			for (CalendarBooking relatedCalendarBooking :
					relatedCalendarBookings) {

				if (relatedCalendarBooking.equals(calendarBooking)) {
					continue;
				}

				calendarBookingLocalService.updateStatus(
					userId, relatedCalendarBooking, trashEntry.getStatus(),
					serviceContext);
			}
		}

		return calendarBooking;
	}

	@Override
	public List<CalendarBooking> search(
		long companyId, long[] groupIds, long[] calendarIds,
		long[] calendarResourceIds, long parentCalendarBookingId,
		String keywords, long startTime, long endTime, boolean recurring,
		int[] statuses, int start, int end,
		OrderByComparator<CalendarBooking> orderByComparator) {

		List<CalendarBooking> calendarBookings =
			calendarBookingFinder.findByKeywords(
				companyId, groupIds, calendarIds, calendarResourceIds,
				parentCalendarBookingId, keywords, startTime, endTime,
				recurring, statuses, start, end, orderByComparator);

		if (recurring) {
			calendarBookings = RecurrenceUtil.expandCalendarBookings(
				calendarBookings, startTime, endTime);
		}

		return calendarBookings;
	}

	@Override
	public List<CalendarBooking> search(
		long companyId, long[] groupIds, long[] calendarIds,
		long[] calendarResourceIds, long parentCalendarBookingId, String title,
		String description, String location, long startTime, long endTime,
		boolean recurring, int[] statuses, boolean andOperator, int start,
		int end, OrderByComparator<CalendarBooking> orderByComparator) {

		List<CalendarBooking> calendarBookings =
			calendarBookingFinder.findByC_G_C_C_P_T_D_L_S_E_S(
				companyId, groupIds, calendarIds, calendarResourceIds,
				parentCalendarBookingId, title, description, location,
				startTime, endTime, recurring, statuses, andOperator, start,
				end, orderByComparator);

		if (recurring) {
			calendarBookings = RecurrenceUtil.expandCalendarBookings(
				calendarBookings, startTime, endTime);
		}

		return calendarBookings;
	}

	@Override
	public int searchCount(
		long companyId, long[] groupIds, long[] calendarIds,
		long[] calendarResourceIds, long parentCalendarBookingId,
		String keywords, long startTime, long endTime, int[] statuses) {

		return calendarBookingFinder.countByKeywords(
			companyId, groupIds, calendarIds, calendarResourceIds,
			parentCalendarBookingId, keywords, startTime, endTime, statuses);
	}

	@Override
	public int searchCount(
		long companyId, long[] groupIds, long[] calendarIds,
		long[] calendarResourceIds, long parentCalendarBookingId, String title,
		String description, String location, long startTime, long endTime,
		int[] statuses, boolean andOperator) {

		return calendarBookingFinder.countByC_G_C_C_P_T_D_L_S_E_S(
			companyId, groupIds, calendarIds, calendarResourceIds,
			parentCalendarBookingId, title, description, location, startTime,
			endTime, statuses, andOperator);
	}

	@Override
	public void updateAsset(
			long userId, CalendarBooking calendarBooking,
			long[] assetCategoryIds, String[] assetTagNames,
			long[] assetLinkEntryIds, Double priority)
		throws PortalException {

		boolean visible = false;

		if (calendarBooking.isApproved()) {
			visible = true;
		}

		String summary = HtmlUtil.extractText(
			StringUtil.shorten(calendarBooking.getDescription(), 500));

		AssetEntry assetEntry = assetEntryLocalService.updateEntry(
			userId, calendarBooking.getGroupId(),
			calendarBooking.getCreateDate(), calendarBooking.getModifiedDate(),
			CalendarBooking.class.getName(),
			calendarBooking.getCalendarBookingId(), calendarBooking.getUuid(),
			0, assetCategoryIds, assetTagNames, visible, null, null, null,
			ContentTypes.TEXT_HTML, calendarBooking.getTitle(),
			calendarBooking.getDescription(), summary, null, null, 0, 0,
			priority);

		assetLinkLocalService.updateLinks(
			userId, assetEntry.getEntryId(), assetLinkEntryIds,
			AssetLinkConstants.TYPE_RELATED);
	}

	@Override
	public CalendarBooking updateCalendarBooking(
			long userId, long calendarBookingId, long calendarId,
			long[] childCalendarIds, Map<Locale, String> titleMap,
			Map<Locale, String> descriptionMap, String location, long startTime,
			long endTime, boolean allDay, String recurrence, long firstReminder,
			String firstReminderType, long secondReminder,
			String secondReminderType, ServiceContext serviceContext)
		throws PortalException {

		// Calendar booking

		Calendar calendar = calendarPersistence.findByPrimaryKey(calendarId);
		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		for (Locale locale : descriptionMap.keySet()) {
			String sanitizedDescription = SanitizerUtil.sanitize(
				calendar.getCompanyId(), calendar.getGroupId(), userId,
				CalendarBooking.class.getName(), calendarBookingId,
				ContentTypes.TEXT_HTML, Sanitizer.MODE_ALL,
				descriptionMap.get(locale), null);

			descriptionMap.put(locale, sanitizedDescription);
		}

		java.util.Calendar startTimeJCalendar = JCalendarUtil.getJCalendar(
			startTime);
		java.util.Calendar endTimeJCalendar = JCalendarUtil.getJCalendar(
			endTime);

		if (allDay) {
			startTimeJCalendar = JCalendarUtil.toMidnightJCalendar(
				startTimeJCalendar);
			endTimeJCalendar = JCalendarUtil.toLastHourJCalendar(
				endTimeJCalendar);
		}

		if (firstReminder < secondReminder) {
			long originalSecondReminder = secondReminder;

			secondReminder = firstReminder;
			firstReminder = originalSecondReminder;
		}

		validate(startTimeJCalendar, endTimeJCalendar, recurrence);

		calendarBooking.setGroupId(calendar.getGroupId());
		calendarBooking.setModifiedDate(serviceContext.getModifiedDate(null));
		calendarBooking.setCalendarId(calendarId);

		Map<Locale, String> updatedTitleMap = calendarBooking.getTitleMap();

		updatedTitleMap.putAll(titleMap);

		calendarBooking.setTitleMap(
			updatedTitleMap, serviceContext.getLocale());

		Map<Locale, String> updatedDescriptionMap =
			calendarBooking.getDescriptionMap();

		updatedDescriptionMap.putAll(descriptionMap);

		calendarBooking.setDescriptionMap(updatedDescriptionMap);

		calendarBooking.setLocation(location);
		calendarBooking.setStartTime(startTimeJCalendar.getTimeInMillis());
		calendarBooking.setEndTime(endTimeJCalendar.getTimeInMillis());
		calendarBooking.setAllDay(allDay);

		if (Validator.isNull(calendarBooking.getMasterRecurrence())) {
			calendarBooking.setRecurrence(recurrence);
			calendarBooking.setMasterRecurrence(recurrence);
		}

		calendarBooking.setMasterRecurrence(recurrence);
		calendarBooking.setFirstReminder(firstReminder);
		calendarBooking.setFirstReminderType(firstReminderType);
		calendarBooking.setSecondReminder(secondReminder);
		calendarBooking.setSecondReminderType(secondReminderType);

		if (!calendarBooking.isPending() || !calendarBooking.isDraft()) {
			calendarBooking.setStatus(WorkflowConstants.STATUS_DRAFT);
		}

		calendarBooking.setExpandoBridgeAttributes(serviceContext);

		calendarBookingPersistence.update(calendarBooking);

		addChildCalendarBookings(
			calendarBooking, childCalendarIds, serviceContext);

		// Asset

		updateAsset(
			userId, calendarBooking, serviceContext.getAssetCategoryIds(),
			serviceContext.getAssetTagNames(),
			serviceContext.getAssetLinkEntryIds(),
			serviceContext.getAssetPriority());

		// Social

		socialActivityLocalService.addActivity(
			userId, calendarBooking.getGroupId(),
			CalendarBooking.class.getName(), calendarBookingId,
			CalendarActivityKeys.UPDATE_CALENDAR_BOOKING,
			getExtraDataJSON(calendarBooking), 0);

		// Notifications

		sendNotification(
			calendarBooking, NotificationTemplateType.UPDATE, serviceContext);

		// Workflow

		if (calendarBooking.isMasterBooking()) {
			WorkflowHandlerRegistryUtil.startWorkflowInstance(
				calendarBooking.getCompanyId(), calendarBooking.getGroupId(),
				userId, CalendarBooking.class.getName(),
				calendarBooking.getCalendarBookingId(), calendarBooking,
				serviceContext);
		}

		return calendarBooking;
	}

	@Override
	public CalendarBooking updateCalendarBooking(
			long userId, long calendarBookingId, long calendarId,
			Map<Locale, String> titleMap, Map<Locale, String> descriptionMap,
			String location, long startTime, long endTime, boolean allDay,
			String recurrence, long firstReminder, String firstReminderType,
			long secondReminder, String secondReminderType,
			ServiceContext serviceContext)
		throws PortalException {

		long[] childCalendarIds = getChildCalendarIds(
			calendarBookingId, calendarId);

		return updateCalendarBooking(
			userId, calendarBookingId, calendarId, childCalendarIds, titleMap,
			descriptionMap, location, startTime, endTime, allDay, recurrence,
			firstReminder, firstReminderType, secondReminder,
			secondReminderType, serviceContext);
	}

	@Override
	public CalendarBooking updateCalendarBookingInstance(
			long userId, long calendarBookingId, int instanceIndex,
			long calendarId, long[] childCalendarIds,
			Map<Locale, String> titleMap, Map<Locale, String> descriptionMap,
			String location, long startTime, long endTime, boolean allDay,
			String masterRecurrence, boolean allFollowing, long firstReminder,
			String firstReminderType, long secondReminder,
			String secondReminderType, ServiceContext serviceContext)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		CalendarBooking addedCalendarBooking = calendarBooking;

		if ((instanceIndex == 0) && allFollowing) {
			return updateRecurringCalendarBooking(
				userId, calendarBookingId, calendarId, childCalendarIds,
				titleMap, descriptionMap, location, startTime, endTime, allDay,
				masterRecurrence, firstReminder, firstReminderType,
				secondReminder, secondReminderType, serviceContext);
		}

		String oldMasterRecurrence = calendarBooking.getMasterRecurrence();

		java.util.Calendar startJCalendar = JCalendarUtil.getJCalendar(
			startTime);

		if (allFollowing) {
			List<String> unchangedList = getUnchangedList(
				calendarBooking, calendarId, titleMap, descriptionMap, location,
				startTime, endTime, allDay, masterRecurrence, firstReminder,
				firstReminderType, secondReminder, secondReminderType);

			List<CalendarBooking> relatedCalendarBookings =
				getRelatedRecurringCalendarBookings(calendarBookingId);

			List<CalendarBooking> followingCalendarBookings = new ArrayList<>();

			List<CalendarBooking> followingRecurringCalendarBookings =
				new ArrayList<>();

			CalendarBooking splitCalendarBooking = null;

			for (CalendarBooking relatedCalendarBooking :
					relatedCalendarBookings) {

				if (!JCalendarUtil.isEarlierDay(
						relatedCalendarBooking.getStartTime(), startTime))  {

					followingCalendarBookings.add(relatedCalendarBooking);

					if (relatedCalendarBooking.getRecurrenceObj() != null) {
						followingRecurringCalendarBookings.add(
							relatedCalendarBooking);
					}
				}
				else {
					Recurrence relatedRecurrenceObj =
						relatedCalendarBooking.getRecurrenceObj();

					if (relatedRecurrenceObj != null) {
						java.util.Calendar relatedUntilJCalendar =
							relatedRecurrenceObj.getUntilJCalendar();

						if ((relatedUntilJCalendar == null) ||
								!JCalendarUtil.isEarlierDay(
									relatedUntilJCalendar, startJCalendar)) {

							splitCalendarBooking = relatedCalendarBooking;
						}
					}
				}
			}

			if (splitCalendarBooking != null) {
				boolean updatedSplitRecurrence = false;

				Recurrence newRecurrenceObj =
					splitCalendarBooking.getRecurrenceObj();

				Recurrence splitRecurrenceObj = newRecurrenceObj.clone();

				if (splitRecurrenceObj.getCount() > 0) {
					splitRecurrenceObj.setCount(
						calendarBooking.getMasterRecurrenceObj().getCount() -
							instanceIndex);
				}

				deleteCalendarBookingInstance(
					splitCalendarBooking, startTime, true, false);



				List<java.util.Calendar> exceptionJCalendars = new ArrayList<>(
					splitRecurrenceObj.getExceptionJCalendars());

				for (java.util.Calendar exceptionJCalendar :
						exceptionJCalendars) {

					if (JCalendarUtil.isEarlierDay(
							exceptionJCalendar, startJCalendar)) {

						newRecurrenceObj.removeExceptionDate(
							exceptionJCalendar);
					}
					else {
						splitRecurrenceObj.removeExceptionDate(
							exceptionJCalendar);

						updatedSplitRecurrence = true;
					}
				}

				if (updatedSplitRecurrence) {
					String splitRecurrence = RecurrenceSerializer.serialize(
						splitRecurrenceObj);

					splitCalendarBooking.setRecurrence(splitRecurrence);

					calendarBookingPersistence.update(splitCalendarBooking);
				}

				if (!followingRecurringCalendarBookings.isEmpty()) {
					long untilTime = getEarliestStartTime(
						followingRecurringCalendarBookings) - JCalendarUtil.DAY;

					newRecurrenceObj.setUntilJCalendar(
						JCalendarUtil.getJCalendar(untilTime));
				}

				String newRecurrence =
					RecurrenceSerializer.serialize(newRecurrenceObj);

				Map<Locale, String> updatedTitleMap =
					splitCalendarBooking.getTitleMap();

				updatedTitleMap.putAll(titleMap);

				Map<Locale, String> updatedDescriptionMap =
					splitCalendarBooking.getDescriptionMap();

				updatedDescriptionMap.putAll(descriptionMap);

				long splitStartTime = JCalendarUtil.convertTimeToNewDay(
					splitCalendarBooking.getStartTime(), startTime);

				long splitEndTime = JCalendarUtil.convertTimeToNewDay(
					splitCalendarBooking.getEndTime(), endTime);

				addedCalendarBooking = addCalendarBooking(
					userId, splitCalendarBooking.getCalendarId(),
					getChildCalendarIds(
						splitCalendarBooking.getCalendarBookingId(),
						splitCalendarBooking.getCalendarId()),
					CalendarBookingConstants.PARENT_CALENDAR_BOOKING_ID_DEFAULT,
					splitCalendarBooking.getRecurringCalendarBookingId(),
					updatedTitleMap, updatedDescriptionMap,
					splitCalendarBooking.getLocation(), splitStartTime,
					splitEndTime, splitCalendarBooking.getAllDay(),
					newRecurrence, splitCalendarBooking.getMasterRecurrence(),
					splitCalendarBooking.getFirstReminder(),
					splitCalendarBooking.getFirstReminderType(),
					splitCalendarBooking.getSecondReminder(),
					splitCalendarBooking.getSecondReminderType(),
					serviceContext);

				followingCalendarBookings.add(addedCalendarBooking);
				followingRecurringCalendarBookings.add(addedCalendarBooking);
			}

			updateCalendarBookingsByChanges(
				userId, calendarId, childCalendarIds, titleMap, descriptionMap,
				location, startTime, endTime, allDay,
				splitCalendarBooking.getMasterRecurrence(), firstReminder,
				firstReminderType, secondReminder, secondReminderType,
				serviceContext, followingCalendarBookings, unchangedList);
		}
		else {
			deleteCalendarBookingInstance(
				calendarBooking, startTime, false, false);

			String recurrence = StringPool.BLANK;

			Map<Locale, String> updatedTitleMap = calendarBooking.getTitleMap();

			updatedTitleMap.putAll(titleMap);

			Map<Locale, String> updatedDescriptionMap =
				calendarBooking.getDescriptionMap();

			updatedDescriptionMap.putAll(descriptionMap);

			addedCalendarBooking = addCalendarBooking(
				userId, calendarId, childCalendarIds,
				CalendarBookingConstants.PARENT_CALENDAR_BOOKING_ID_DEFAULT,
				calendarBooking.getRecurringCalendarBookingId(),
				updatedTitleMap, updatedDescriptionMap, location, startTime,
				endTime, allDay, recurrence, masterRecurrence, firstReminder,
				firstReminderType, secondReminder, secondReminderType,
				serviceContext);
		}

		return addedCalendarBooking;
	}

	@Override
	public CalendarBooking updateCalendarBookingInstance(
			long userId, long calendarBookingId, int instanceIndex,
			long calendarId, Map<Locale, String> titleMap,
			Map<Locale, String> descriptionMap, String location, long startTime,
			long endTime, boolean allDay, String recurrence,
			boolean allFollowing, long firstReminder, String firstReminderType,
			long secondReminder, String secondReminderType,
			ServiceContext serviceContext)
		throws PortalException {

		long[] childCalendarIds = getChildCalendarIds(
			calendarBookingId, calendarId);

		return updateCalendarBookingInstance(
			userId, calendarBookingId, instanceIndex, calendarId,
			childCalendarIds, titleMap, descriptionMap, location, startTime,
			endTime, allDay, recurrence, allFollowing, firstReminder,
			firstReminderType, secondReminder, secondReminderType,
			serviceContext);
	}

	public CalendarBooking updateMasterRecurrence(
			long calendarBookingId, String newRecurrence, long userId,
			long startTime, boolean allFollowing, boolean updateInstance,
			boolean checkChanges, ServiceContext serviceContext)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		if (updateInstance && !allFollowing) {
			return calendarBooking;
		}

		String oldRecurrence = calendarBooking.getMasterRecurrence();

		if (oldRecurrence.equals(newRecurrence) && checkChanges) {
			return calendarBooking;
		}

		Recurrence newRecurrenceObj = RecurrenceSerializer.deserialize(
			newRecurrence, calendarBooking.getTimeZone());

		List<java.util.Calendar> exceptionJCalendars = new ArrayList<>(
			newRecurrenceObj.getExceptionJCalendars());

		java.util.Calendar startJCalendar = JCalendarUtil.getJCalendar(
			startTime);

		List<CalendarBooking> calendarBookings =
			getRelatedRecurringCalendarBookings(
				calendarBooking.getCalendarBookingId());

		long earliestStartTime = getEarliestStartTime(calendarBookings);

		java.util.Calendar earliestStartJCalendar = JCalendarUtil.getJCalendar(
			earliestStartTime);

		for (java.util.Calendar exceptionJCalendar : exceptionJCalendars) {
			if (!hasCalendarDate(
					newRecurrenceObj, exceptionJCalendar, startJCalendar,
					earliestStartJCalendar)) {

				newRecurrenceObj.removeExceptionDate(exceptionJCalendar);
			}
		}

		Recurrence oldMasterRecurrenceObj =
			calendarBooking.getMasterRecurrenceObj();

		java.util.Calendar oldMasterRecurrenceUntilDate =
			oldMasterRecurrenceObj.getUntilJCalendar();

		newRecurrence = RecurrenceSerializer.serialize(newRecurrenceObj);

		if (!updateInstance) {
			for (CalendarBooking curCalendarBooking : calendarBookings) {
				if (isInRecurrenceScope(
						curCalendarBooking, newRecurrenceObj,
						earliestStartJCalendar)) {

					curCalendarBooking.setMasterRecurrence(newRecurrence);

					Recurrence curRecurrenceObj =
						curCalendarBooking.getRecurrenceObj();

					if (curRecurrenceObj != null) {
						java.util.Calendar curRecurrenceUntilDate =
							curRecurrenceObj.getUntilJCalendar();

						boolean modifyUntilDate = false;

						if (Validator.isNull(curRecurrenceUntilDate)) {
							modifyUntilDate = Validator.isNull(
								oldMasterRecurrenceUntilDate);
						}
						else {
							modifyUntilDate = curRecurrenceUntilDate.equals(
								oldMasterRecurrenceUntilDate);
						}

						curRecurrenceObj = convertToNewRecurrenceObj(
							curRecurrenceObj, newRecurrenceObj,
							earliestStartJCalendar, earliestStartJCalendar,
							modifyUntilDate);

						curCalendarBooking.setRecurrence(
							RecurrenceSerializer.serialize(curRecurrenceObj));
					}

					calendarBookingPersistence.update(curCalendarBooking);
				}
				else {
					deleteCalendarBooking(curCalendarBooking, false);
				}
			}

			try {
				calendarBookingPersistence.findByPrimaryKey(calendarBookingId);
			}
			catch (PortalException pe) {
				calendarBooking = calendarBookingPersistence.findByPrimaryKey(
					calendarBooking.getRecurringCalendarBookingId());
			}

			return calendarBooking;
		}

		else {
			List<CalendarBooking> followingCalendarBookings = new ArrayList<>();

			List<CalendarBooking> followingRecurringCalendarBookings =
				new ArrayList<>();

			List<CalendarBooking> previousCalendarBookings = new ArrayList<>();

			CalendarBooking splitCalendarBooking = null;

			CalendarBooking newCalendarBooking = calendarBooking;

			for (CalendarBooking relatedCalendarBooking : calendarBookings) {
				long relatedStartTime = relatedCalendarBooking.getStartTime();

				long relatedStartTimeToday = JCalendarUtil.convertTimeToNewDay(
					relatedStartTime, startTime);

				if (relatedStartTime >= relatedStartTimeToday) {
					followingCalendarBookings.add(relatedCalendarBooking);

					if (relatedCalendarBooking.getRecurrenceObj() != null) {
						followingRecurringCalendarBookings.add(
							relatedCalendarBooking);
					}
				}
				else {
					previousCalendarBookings.add(relatedCalendarBooking);

					Recurrence relatedRecurrenceObj =
						relatedCalendarBooking.getRecurrenceObj();

					if (relatedRecurrenceObj != null) {
						java.util.Calendar relatedUntilJCalendar =
							relatedRecurrenceObj.getUntilJCalendar();

						if (relatedUntilJCalendar == null) {
							splitCalendarBooking = relatedCalendarBooking;

							continue;
						}

						java.util.Calendar adjustedRelatedUntilJCalendar =
							JCalendarUtil.toLastHourJCalendar(
								relatedUntilJCalendar);

						adjustedRelatedUntilJCalendar.add(
							java.util.Calendar.DATE, 1);

						java.util.Calendar adjustedStartJCalendar =
							JCalendarUtil.toLastHourJCalendar(startJCalendar);

						if (adjustedRelatedUntilJCalendar.after(
								adjustedStartJCalendar)) {

							splitCalendarBooking = relatedCalendarBooking;
						}
					}
				}
			}

			if (Validator.isNotNull(splitCalendarBooking)) {
				boolean modifyUntilDate =
					followingRecurringCalendarBookings.isEmpty();

				long calendarId = splitCalendarBooking.getCalendarId();

				long newStartTime = JCalendarUtil.convertTimeToNewDay(
					splitCalendarBooking.getStartTime(), startTime);

				long newEndTime = JCalendarUtil.convertTimeToNewDay(
					splitCalendarBooking.getEndTime(), startTime);

				Recurrence addRecurrenceObj =
					splitCalendarBooking.getRecurrenceObj().clone();

				addRecurrenceObj = convertToNewRecurrenceObj(
					addRecurrenceObj, newRecurrenceObj,
					JCalendarUtil.getJCalendar(startTime),
					earliestStartJCalendar, modifyUntilDate);

				if (!followingRecurringCalendarBookings.isEmpty()) {
					long untilTime = getEarliestStartTime(
						followingRecurringCalendarBookings);

					java.util.Calendar untilJCalendar =
						JCalendarUtil.getJCalendar(untilTime);

					untilJCalendar.add(java.util.Calendar.DATE, -1);

					java.util.Calendar finalJCalendar =
						RecurrenceUtil.getFinalCalendarBookingTime(
							addRecurrenceObj,
							JCalendarUtil.getJCalendar(startTime));

					if ((finalJCalendar == null) ||
						finalJCalendar.after(untilJCalendar)) {

						addRecurrenceObj.setUntilJCalendar(untilJCalendar);
						addRecurrenceObj.setCount(0);
					}
				}

				String addRecurrence = RecurrenceSerializer.serialize(
					addRecurrenceObj);

				newCalendarBooking = addCalendarBooking(
					userId, calendarId,
					getChildCalendarIds(
						splitCalendarBooking.getCalendarBookingId(),
						calendarId),
					0, 0, splitCalendarBooking.getTitleMap(),
					splitCalendarBooking.getDescriptionMap(),
					splitCalendarBooking.getLocation(), newStartTime,
					newEndTime, splitCalendarBooking.getAllDay(), addRecurrence,
					newRecurrence, splitCalendarBooking.getFirstReminder(),
					splitCalendarBooking.getFirstReminderType(),
					splitCalendarBooking.getSecondReminder(),
					splitCalendarBooking.getSecondReminderType(),
					serviceContext);
			}

			long newCalendarBookingId =
				newCalendarBooking.getCalendarBookingId();

			for (CalendarBooking followingCalendarBooking :
					followingCalendarBookings) {

				followingCalendarBooking.setRecurringCalendarBookingId(
					newCalendarBookingId);

				calendarBookingPersistence.update(followingCalendarBooking);
			}

			java.util.Calendar untilJCalendar =
				(java.util.Calendar)startJCalendar.clone();

			untilJCalendar.add(java.util.Calendar.DATE, -1);

			oldMasterRecurrenceObj.setUntilJCalendar(untilJCalendar);

			String oldMasterRecurrence = RecurrenceSerializer.serialize(
				oldMasterRecurrenceObj);

			for (CalendarBooking previousCalendarBooking :
					previousCalendarBookings) {

				previousCalendarBooking.setMasterRecurrence(
					oldMasterRecurrence);

				calendarBookingPersistence.update(previousCalendarBooking);
			}

			if (splitCalendarBooking != null) {
				deleteCalendarBookingInstance(
					splitCalendarBooking, startTime, true, true);

				if (splitCalendarBooking.getCalendarBookingId() ==
						calendarBookingId) {

					return updateMasterRecurrence(
						newCalendarBookingId, newRecurrence, userId, startTime,
						false, false, false, serviceContext);
				}
			}

			return updateMasterRecurrence(
				calendarBookingId, newRecurrence, userId, startTime, false,
				false, false, serviceContext);
		}
	}

	@Override
	public CalendarBooking updateRecurringCalendarBooking(
			long userId, long calendarBookingId, long calendarId,
			long[] childCalendarIds, Map<Locale, String> titleMap,
			Map<Locale, String> descriptionMap, String location, long startTime,
			long endTime, boolean allDay, String recurrence, long firstReminder,
			String firstReminderType, long secondReminder,
			String secondReminderType, ServiceContext serviceContext)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		List<CalendarBooking> relatedCalendarBookings =
			getRelatedRecurringCalendarBookings(calendarBookingId);

		List<String> unchangedList = getUnchangedList(
			calendarBooking, calendarId, titleMap, descriptionMap, location,
			startTime, endTime, allDay, recurrence, firstReminder,
			firstReminderType, secondReminder, secondReminderType);

		updateCalendarBookingsByChanges(
			userId, calendarId, childCalendarIds, titleMap, descriptionMap,
			location, startTime, endTime, allDay,
			calendarBooking.getMasterRecurrence(), firstReminder,
			firstReminderType, secondReminder, secondReminderType,
			serviceContext, relatedCalendarBookings, unchangedList);

		return calendarBooking;
	}

	@Indexable(type = IndexableType.REINDEX)
	@Override
	public CalendarBooking updateStatus(
			long userId, CalendarBooking calendarBooking, int status,
			ServiceContext serviceContext)
		throws PortalException {

		// Calendar booking

		User user = userPersistence.findByPrimaryKey(userId);
		Date now = new Date();

		int oldStatus = calendarBooking.getStatus();

		calendarBooking.setModifiedDate(serviceContext.getModifiedDate(now));
		calendarBooking.setStatus(status);
		calendarBooking.setStatusByUserId(user.getUserId());
		calendarBooking.setStatusByUserName(user.getFullName());
		calendarBooking.setStatusDate(serviceContext.getModifiedDate(now));

		calendarBookingPersistence.update(calendarBooking);

		// Child calendar bookings

		if (status == CalendarBookingWorkflowConstants.STATUS_IN_TRASH) {
			List<CalendarBooking> childCalendarBookings =
				calendarBooking.getChildCalendarBookings();

			for (CalendarBooking childCalendarBooking : childCalendarBookings) {
				if (childCalendarBooking.equals(calendarBooking)) {
					continue;
				}

				updateStatus(
					userId, childCalendarBooking,
					CalendarBookingWorkflowConstants.STATUS_IN_TRASH,
					serviceContext);
			}
		}
		else if (oldStatus ==
					CalendarBookingWorkflowConstants.STATUS_IN_TRASH) {

			List<CalendarBooking> childCalendarBookings =
				calendarBooking.getChildCalendarBookings();

			for (CalendarBooking childCalendarBooking : childCalendarBookings) {
				if (childCalendarBooking.equals(calendarBooking)) {
					continue;
				}

				updateStatus(
					userId, childCalendarBooking,
					CalendarBookingWorkflowConstants.STATUS_PENDING,
					serviceContext);
			}
		}
		else if (status == CalendarBookingWorkflowConstants.STATUS_APPROVED) {
			List<CalendarBooking> childCalendarBookings =
				calendarBooking.getChildCalendarBookings();

			for (CalendarBooking childCalendarBooking : childCalendarBookings) {
				if (childCalendarBooking.equals(calendarBooking)) {
					continue;
				}

				if (childCalendarBooking.getStatus() ==
						CalendarBookingWorkflowConstants.
							STATUS_MASTER_PENDING) {

					updateStatus(
						userId, childCalendarBooking,
						CalendarBookingWorkflowConstants.STATUS_PENDING,
						serviceContext);
				}
			}
		}
		else {
			List<CalendarBooking> childCalendarBookings =
				calendarBooking.getChildCalendarBookings();

			for (CalendarBooking childCalendarBooking : childCalendarBookings) {
				if (childCalendarBooking.equals(calendarBooking)) {
					continue;
				}

				updateStatus(
					userId, childCalendarBooking,
					CalendarBookingWorkflowConstants.STATUS_MASTER_PENDING,
					serviceContext);
			}
		}

		// Asset

		if (status == CalendarBookingWorkflowConstants.STATUS_APPROVED) {
			assetEntryLocalService.updateVisible(
				CalendarBooking.class.getName(),
				calendarBooking.getCalendarBookingId(), true);
		}
		else if (status == CalendarBookingWorkflowConstants.STATUS_IN_TRASH) {
			assetEntryLocalService.updateVisible(
				CalendarBooking.class.getName(),
				calendarBooking.getCalendarBookingId(), false);
		}

		// Trash

		if (oldStatus == WorkflowConstants.STATUS_IN_TRASH) {
			trashEntryLocalService.deleteEntry(
				CalendarBooking.class.getName(),
				calendarBooking.getCalendarBookingId());
		}

		if (status == CalendarBookingWorkflowConstants.STATUS_IN_TRASH) {
			if (calendarBooking.isMasterRecurringBooking()) {
				if (calendarBooking.isMasterBooking()) {
					trashEntryLocalService.addTrashEntry(
						userId, calendarBooking.getGroupId(),
						CalendarBooking.class.getName(),
						calendarBooking.getCalendarBookingId(),
						calendarBooking.getUuid(), null, oldStatus, null, null);
				}
				else {
					trashEntryLocalService.addTrashEntry(
						userId, calendarBooking.getGroupId(),
						CalendarBooking.class.getName(),
						calendarBooking.getCalendarBookingId(),
						calendarBooking.getUuid(), null,
						CalendarBookingWorkflowConstants.STATUS_PENDING, null,
						null);
				}

				sendNotification(
					calendarBooking, NotificationTemplateType.MOVED_TO_TRASH,
					serviceContext);
			}
		}

		return calendarBooking;
	}

	@Override
	public CalendarBooking updateStatus(
			long userId, long calendarBookingId, int status,
			ServiceContext serviceContext)
		throws PortalException {

		CalendarBooking calendarBooking =
			calendarBookingPersistence.findByPrimaryKey(calendarBookingId);

		return calendarBookingLocalService.updateStatus(
			userId, calendarBooking, status, serviceContext);
	}

	protected void addChildCalendarBookings(
			CalendarBooking calendarBooking, long[] childCalendarIds,
			ServiceContext serviceContext)
		throws PortalException {

		if (!calendarBooking.isMasterBooking()) {
			return;
		}

		Map<Long, CalendarBooking> childCalendarBookingMap = new HashMap<>();

		List<CalendarBooking> childCalendarBookings =
			calendarBookingPersistence.findByParentCalendarBookingId(
				calendarBooking.getCalendarBookingId());

		for (CalendarBooking childCalendarBooking : childCalendarBookings) {
			if (childCalendarBooking.isMasterBooking() ||
				(childCalendarBooking.isDenied() &&
				 ArrayUtil.contains(
					 childCalendarIds, childCalendarBooking.getCalendarId()))) {

				continue;
			}

			deleteCalendarBooking(childCalendarBooking.getCalendarBookingId());

			childCalendarBookingMap.put(
				childCalendarBooking.getCalendarId(), childCalendarBooking);
		}

		for (long calendarId : childCalendarIds) {
			int count = calendarBookingPersistence.countByC_P(
				calendarId, calendarBooking.getCalendarBookingId());

			if (count > 0) {
				continue;
			}

			serviceContext.setAttribute("sendNotification", false);

			CalendarBooking childCalendarBooking = addCalendarBooking(
				calendarBooking.getUserId(), calendarId, new long[0],
				calendarBooking.getRecurringCalendarBookingId(),
				calendarBooking.getCalendarBookingId(),
				calendarBooking.getTitleMap(),
				calendarBooking.getDescriptionMap(),
				calendarBooking.getLocation(), calendarBooking.getStartTime(),
				calendarBooking.getEndTime(), calendarBooking.getAllDay(),
				calendarBooking.getRecurrence(),
				calendarBooking.getMasterRecurrence(),
				calendarBooking.getFirstReminder(),
				calendarBooking.getFirstReminderType(),
				calendarBooking.getSecondReminder(),
				calendarBooking.getSecondReminderType(), serviceContext);

			serviceContext.setAttribute("sendNotification", true);

			int workflowAction = GetterUtil.getInteger(
				serviceContext.getAttribute("workflowAction"));

			if (childCalendarBookingMap.containsKey(calendarId)) {
				CalendarBooking oldChildCalendarBooking =
					childCalendarBookingMap.get(calendarId);

				if ((calendarBooking.getStartTime() ==
						oldChildCalendarBooking.getStartTime()) &&
					(calendarBooking.getEndTime() ==
						oldChildCalendarBooking.getEndTime()) &&
					(workflowAction != WorkflowConstants.ACTION_SAVE_DRAFT)) {

					updateStatus(
						childCalendarBooking.getUserId(), childCalendarBooking,
						oldChildCalendarBooking.getStatus(), serviceContext);
				}
			}

			NotificationTemplateType notificationTemplateType =
				NotificationTemplateType.INVITE;

			if (childCalendarBookingMap.containsKey(
					childCalendarBooking.getCalendarId())) {

				notificationTemplateType = NotificationTemplateType.UPDATE;
			}

			sendNotification(
				childCalendarBooking, notificationTemplateType, serviceContext);
		}
	}

	protected Recurrence convertToNewRecurrenceObj(
		Recurrence oldRecurrenceObj, Recurrence newRecurrenceObj,
		java.util.Calendar startJCalendar,
		java.util.Calendar masterStartJCalendar, boolean modifyUntilDate) {

		if ((oldRecurrenceObj == null) || (newRecurrenceObj == null)) {
			return null;
		}

		oldRecurrenceObj.setFrequency(newRecurrenceObj.getFrequency());
		oldRecurrenceObj.setInterval(newRecurrenceObj.getInterval());
		oldRecurrenceObj.setMonths(newRecurrenceObj.getMonths());
		oldRecurrenceObj.setPositionalWeekdays(
			newRecurrenceObj.getPositionalWeekdays());

		java.util.Calendar newEndTime =
			RecurrenceUtil.getFinalCalendarBookingTime(
				newRecurrenceObj, masterStartJCalendar);

		java.util.Calendar oldEndTime =
			RecurrenceUtil.getFinalCalendarBookingTime(
				oldRecurrenceObj, startJCalendar);

		if (modifyUntilDate || (oldEndTime == null) ||
			((newEndTime != null) && oldEndTime.after(newEndTime))) {

			oldRecurrenceObj.setUntilJCalendar(
				newRecurrenceObj.getUntilJCalendar());
		}

		if ((newRecurrenceObj.getCount() == 0) ||
			(oldRecurrenceObj.getUntilJCalendar() == null)) {

			oldRecurrenceObj.setCount(newRecurrenceObj.getCount());
		}

		List<java.util.Calendar> exceptionJCalendars = new ArrayList<>(
			oldRecurrenceObj.getExceptionJCalendars());

		for (java.util.Calendar exceptionJCalendar : exceptionJCalendars) {
			if (!hasCalendarDate(
					oldRecurrenceObj, exceptionJCalendar, startJCalendar,
					masterStartJCalendar)) {

				oldRecurrenceObj.removeExceptionDate(exceptionJCalendar);
			}
		}

		return oldRecurrenceObj;
	}

	protected String getExtraDataJSON(CalendarBooking calendarBooking) {
		JSONObject jsonObject = JSONFactoryUtil.createJSONObject();

		jsonObject.put("title", calendarBooking.getTitle());

		return jsonObject.toString();
	}

	protected List<String> getUnchangedList(
		CalendarBooking calendarBooking, long calendarId,
		Map<Locale, String> titleMap, Map<Locale, String> descriptionMap,
		String location, long startTime, long endTime, boolean allDay,
		String recurrence, long firstReminder, String firstReminderType,
		long secondReminder, String secondReminderType) {

		List<String> unchangedList = new ArrayList<>();

		if (calendarId == calendarBooking.getCalendarId()) {
			unchangedList.add("calendarId");
		}

		Map<Locale, String> updatedTitleMap = new HashMap<>();

		for (Map.Entry<Locale, String> titleMapEntry : titleMap.entrySet()) {
			if (titleMapEntry.getValue() != null) {
				updatedTitleMap.put(
					titleMapEntry.getKey(), titleMapEntry.getValue());
			}
		}

		if (updatedTitleMap.equals(calendarBooking.getTitleMap())) {
			unchangedList.add("titleMap");
		}

		Map<Locale, String> updatedDescriptionMap = new HashMap<>();

		for (Map.Entry<Locale, String> descriptionMapEntry :
				descriptionMap.entrySet()) {

			if (descriptionMapEntry.getValue() != null) {
				updatedDescriptionMap.put(
					descriptionMapEntry.getKey(),
					descriptionMapEntry.getValue());
			}
		}

		if (updatedDescriptionMap.equals(calendarBooking.getDescriptionMap())) {
			unchangedList.add("descriptionMap");
		}

		if (location.equals(calendarBooking.getLocation())) {
			unchangedList.add("location");
		}

		long newStartTime = JCalendarUtil.convertTimeToNewDay(
			calendarBooking.getStartTime(), startTime);

		long newEndTime = JCalendarUtil.convertTimeToNewDay(
			calendarBooking.getEndTime(), endTime);

		if ((startTime == newStartTime) && (endTime == newEndTime)) {
			unchangedList.add("time");
		}

		if (allDay == calendarBooking.getAllDay()) {
			unchangedList.add("allDay");
		}

		if (firstReminder == calendarBooking.getFirstReminder()) {
			unchangedList.add("firstReminder");
		}

		if (firstReminderType.equals(calendarBooking.getFirstReminderType())) {
			unchangedList.add("firstReminderType");
		}

		if (secondReminder == calendarBooking.getSecondReminder()) {
			unchangedList.add("secondReminder");
		}

		if (secondReminderType.equals(
				calendarBooking.getSecondReminderType())) {

			unchangedList.add("secondReminderType");
		}

		return unchangedList;
	}

	protected boolean hasCalendarDate(
		Recurrence recurrenceObj, java.util.Calendar jCalendar,
		java.util.Calendar startJCalendar,
		java.util.Calendar masterStartJCalendar) {

		if (jCalendar.before(
				JCalendarUtil.toMidnightJCalendar(startJCalendar))) {

			return false;
		}

		java.util.Calendar untilJCalendar =
			RecurrenceUtil.getFinalCalendarBookingTime(
				recurrenceObj, masterStartJCalendar);

		if ((untilJCalendar != null) &&
			jCalendar.after(
				JCalendarUtil.toLastHourJCalendar(untilJCalendar))) {

			return false;
		}

		Frequency frequency = recurrenceObj.getFrequency();

		if (frequency.equals(Frequency.DAILY)) {
			long dayDifference = JCalendarUtil.getDaysBetween(
				startJCalendar, jCalendar);

			if ((dayDifference % recurrenceObj.getInterval()) == 0) {
				return true;
			}

			return false;
		}

		if (frequency.equals(Frequency.WEEKLY)) {
			List<Weekday> weekdays = recurrenceObj.getWeekdays();

			if (!weekdays.contains(Weekday.getWeekday(jCalendar))) {
				return false;
			}

			long weekDifference = JCalendarUtil.getWeeksBetween(
				startJCalendar, jCalendar);

			if ((weekDifference % recurrenceObj.getInterval()) == 0) {
				return true;
			}

			return false;
		}

		if (frequency.equals(Frequency.MONTHLY)) {
			List<PositionalWeekday> positionalWeekdays =
				recurrenceObj.getPositionalWeekdays();

			if (ListUtil.isNotEmpty(positionalWeekdays)) {
				boolean contains = false;

				for (PositionalWeekday positionalWeekday : positionalWeekdays) {
					if (JCalendarUtil.isPositionalWeekday(
							jCalendar, positionalWeekday)) {

						contains = true;

						break;
					}
				}

				if (!contains) {
					return false;
				}
			}
			else {
				if (jCalendar.get(java.util.Calendar.DAY_OF_MONTH) !=
						startJCalendar.get(java.util.Calendar.DAY_OF_MONTH)) {

					return false;
				}
			}

			long monthsDifference = JCalendarUtil.getMonthsBetween(
				startJCalendar, jCalendar);

			if ((monthsDifference % recurrenceObj.getInterval()) == 0) {
				return true;
			}

			return false;
		}

		if (frequency.equals(Frequency.YEARLY)) {
			List<Integer> months = recurrenceObj.getMonths();

			if (ListUtil.isEmpty(months)) {
				months.add(startJCalendar.get(java.util.Calendar.MONTH));
			}

			Integer month = jCalendar.get(java.util.Calendar.MONTH);

			if (!months.contains(month)) {
				return false;
			}

			List<PositionalWeekday> positionalWeekdays =
				recurrenceObj.getPositionalWeekdays();

			if (ListUtil.isNotEmpty(positionalWeekdays)) {
				boolean contains = false;

				for (PositionalWeekday positionalWeekday : positionalWeekdays) {
					if (JCalendarUtil.isPositionalWeekday(
							jCalendar, positionalWeekday)) {

						contains = true;

						break;
					}
				}

				if (!contains) {
					return false;
				}
			}
			else {
				if (jCalendar.get(java.util.Calendar.DAY_OF_MONTH) !=
						startJCalendar.get(java.util.Calendar.DAY_OF_MONTH)) {

					return false;
				}
			}

			long yearsDifference = JCalendarUtil.getYearsBetween(
				startJCalendar, jCalendar);

			if ((yearsDifference % recurrenceObj.getInterval()) == 0) {
				return true;
			}

			return false;
		}

		throw new IllegalArgumentException();
	}

	protected boolean hasDateValue(
		CalendarBooking calendarBooking, DateValue dateValue) {

		long startTime = calendarBooking.getStartTime();

		TimeZone timeZone = null;

		if (calendarBooking.isAllDay()) {
			timeZone = TimeZone.getTimeZone(StringPool.UTC);
		}
		else {
			timeZone = calendarBooking.getTimeZone();
		}

		DateValue startDateValue = CalendarBookingIterator.toDateValue(
			startTime, timeZone);

		Recurrence recurrenceObj = calendarBooking.getRecurrenceObj();

		if (recurrenceObj == null) {
			return dateValue.equals(startDateValue);
		}

		if (dateValue.compareTo(startDateValue) < 0) {
			return false;
		}

		if (recurrenceObj.hasExceptionDate(dateValue)) {
			return false;
		}

		try {
			DateValue endDateValue = RecurrenceSerializer.toDateValue(
				recurrenceObj.getUntilJCalendar());

			if (dateValue.compareTo(endDateValue) > 0) {
				return false;
			}
		}
		catch (NullPointerException npe) {
		}

		return true;
	}

	protected boolean isInRecurrenceScope(
		CalendarBooking calendarBooking, Recurrence recurrenceObj,
		java.util.Calendar startJCalendar) {

		java.util.Calendar jCalendar = JCalendarUtil.getJCalendar(
			calendarBooking.getStartTime());

		if (Validator.isNull(recurrenceObj)) {
			return JCalendarUtil.isSameDay(jCalendar, startJCalendar);
		}

		Recurrence oldRecurrenceObj = calendarBooking.getRecurrenceObj();

		if (Validator.isNotNull(oldRecurrenceObj)) {
			java.util.Calendar untilJCalendar =
				RecurrenceUtil.getFinalCalendarBookingTime(
					recurrenceObj, startJCalendar);

			if ((untilJCalendar != null) &&
				jCalendar.after(
					JCalendarUtil.toLastHourJCalendar(untilJCalendar))) {

				return false;
			}

			return true;
		}

		return hasCalendarDate(
			recurrenceObj, jCalendar, startJCalendar, startJCalendar);
	}

	protected void sendNotification(
		CalendarBooking calendarBooking,
		NotificationTemplateType notificationTemplateType,
		ServiceContext serviceContext) {

		boolean sendNotification = ParamUtil.getBoolean(
			serviceContext, "sendNotification", true);

		if (!sendNotification) {
			return;
		}

		try {
			User sender = userLocalService.fetchUser(
				serviceContext.getUserId());

			NotificationType notificationType =
				CalendarServiceConfigurationValues.
					CALENDAR_NOTIFICATION_DEFAULT_TYPE;

			NotificationUtil.notifyCalendarBookingRecipients(
				calendarBooking, notificationType, notificationTemplateType,
				sender);
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn(e, e);
			}
		}
	}

	protected void updateCalendarBookingsByChanges(
			long userId, long calendarId, long[] childCalendarIds,
			Map<Locale, String> titleMap, Map<Locale, String> descriptionMap,
			String location, long startTime, long endTime, boolean allDay,
			String recurrence, long firstReminder, String firstReminderType,
			long secondReminder, String secondReminderType,
			ServiceContext serviceContext,
			List<CalendarBooking> calendarBookings, List<String> unchangedList)
		throws PortalException {

		for (CalendarBooking calendarBooking : calendarBookings) {
			long calendarBookingId = calendarBooking.getCalendarBookingId();

			if (unchangedList.contains("calendarId")) {
				calendarId = calendarBooking.getCalendarId();
			}

			if (unchangedList.contains("titleMap")) {
				titleMap = calendarBooking.getTitleMap();
			}

			if (unchangedList.contains("descriptionMap")) {
				descriptionMap = calendarBooking.getDescriptionMap();
			}

			if (unchangedList.contains("location")) {
				location = calendarBooking.getLocation();
			}

			if (unchangedList.contains("time")) {
				startTime = calendarBooking.getStartTime();
				endTime = calendarBooking.getEndTime();
			}
			else {
				startTime = JCalendarUtil.convertTimeToNewDay(
					startTime, calendarBooking.getStartTime());

				endTime = JCalendarUtil.convertTimeToNewDay(
					endTime, calendarBooking.getEndTime());
			}

			if (unchangedList.contains("allDay")) {
				allDay = calendarBooking.getAllDay();
			}

			if (unchangedList.contains("firstReminder")) {
				firstReminder = calendarBooking.getFirstReminder();
			}

			if (unchangedList.contains("firstReminderType")) {
				firstReminderType = calendarBooking.getFirstReminderType();
			}

			if (unchangedList.contains("secondReminder")) {
				secondReminder = calendarBooking.getSecondReminder();
			}

			if (unchangedList.contains("secondReminderType")) {
				secondReminderType = calendarBooking.getSecondReminderType();
			}

			updateCalendarBooking(
				userId, calendarBookingId, calendarId, childCalendarIds,
				titleMap, descriptionMap, location, startTime, endTime, allDay,
				recurrence, firstReminder, firstReminderType, secondReminder,
				secondReminderType, serviceContext);
		}
	}

	protected void updateChildCalendarBookings(
		CalendarBooking calendarBooking, Date modifiedDate, String recurrence,
		String masterRecurrence) {

		List<CalendarBooking> childCalendarBookings = new ArrayList<>();

		List<CalendarBooking> relatedCalendarBookings = new ArrayList<>();

		if (calendarBooking.isMasterBooking()) {
			childCalendarBookings = getChildCalendarBookings(
				calendarBooking.getCalendarBookingId());
		}
		else {
			childCalendarBookings.add(calendarBooking);
		}

		for (CalendarBooking childCalendarBooking : childCalendarBookings) {
			try {
				relatedCalendarBookings.addAll(
					getRelatedRecurringCalendarBookings(
						childCalendarBooking.getCalendarBookingId()));

				relatedCalendarBookings.remove(childCalendarBooking);
			}
			catch (PortalException pe) {
			}

			childCalendarBooking.setModifiedDate(modifiedDate);
			childCalendarBooking.setRecurrence(recurrence);
			childCalendarBooking.setMasterRecurrence(masterRecurrence);

			calendarBookingPersistence.update(childCalendarBooking);
		}

		for (CalendarBooking relatedCalendarBooking : relatedCalendarBookings) {
			if (!masterRecurrence.equals(
					relatedCalendarBooking.getMasterRecurrence())) {

				relatedCalendarBooking.setModifiedDate(modifiedDate);
				relatedCalendarBooking.setMasterRecurrence(masterRecurrence);

				calendarBookingPersistence.update(relatedCalendarBooking);
			}
		}
	}

	protected void validate(
			java.util.Calendar startTimeJCalendar,
			java.util.Calendar endTimeJCalendar, String recurrence)
		throws PortalException {

		if (startTimeJCalendar.after(endTimeJCalendar)) {
			throw new CalendarBookingDurationException();
		}

		if (Validator.isNull(recurrence)) {
			return;
		}

		Recurrence recurrenceObj = RecurrenceSerializer.deserialize(
			recurrence, startTimeJCalendar.getTimeZone());

		if ((recurrenceObj.getUntilJCalendar() != null) &&
			startTimeJCalendar.compareTo(recurrenceObj.getUntilJCalendar()) >=
				JCalendarUtil.DAY) {

			throw new CalendarBookingRecurrenceException();
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CalendarBookingLocalServiceImpl.class);

}