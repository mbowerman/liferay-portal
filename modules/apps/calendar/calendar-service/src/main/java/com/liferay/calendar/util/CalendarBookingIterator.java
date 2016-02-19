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

package com.liferay.calendar.util;

import com.google.ical.iter.RecurrenceIterator;
import com.google.ical.iter.RecurrenceIteratorFactory;
import com.google.ical.values.DateValue;
import com.google.ical.values.DateValueImpl;

import com.liferay.calendar.model.CalendarBooking;
import com.liferay.calendar.recurrence.Recurrence;
import com.liferay.calendar.service.CalendarBookingLocalServiceUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.TimeZoneUtil;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimeZone;

/**
 * @author Adam Brandizzi
 */
public class CalendarBookingIterator implements Iterator<CalendarBooking> {

	public static DateValue toDateValue(long time, TimeZone timeZone) {
		Calendar jCalendar = JCalendarUtil.getJCalendar(time, timeZone);

		return new DateValueImpl(
			jCalendar.get(Calendar.YEAR), jCalendar.get(Calendar.MONTH) + 1,
			jCalendar.get(Calendar.DAY_OF_MONTH));
	}

	public CalendarBookingIterator(CalendarBooking calendarBooking)
		throws ParseException {

		List<CalendarBooking> calendarBookings = new ArrayList<>();

		try {
			calendarBookings =
				CalendarBookingLocalServiceUtil.
					getRelatedRecurringCalendarBookings(
						calendarBooking.getCalendarBookingId());
		}
		catch (Exception e) {
			calendarBookings.add(calendarBooking);
		}

		_calendarBookings = calendarBookings;

		_calendarBooking = calendarBooking;

		_startTime = CalendarBookingLocalServiceUtil.getEarliestStartTime(
			_calendarBookings);

		_recurrenceIterator =
			RecurrenceIteratorFactory.createRecurrenceIterator(
				calendarBooking.getMasterRecurrence(),
				toDateValue(_startTime, _getTimeZone(_calendarBooking)),
				calendarBooking.getTimeZone());
	}

	@Override
	public boolean hasNext() {
		if (_recurrenceIterator.hasNext() && !_isExceededCount()) {
			return true;
		}

		return false;
	}

	@Override
	public CalendarBooking next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		_currentDateValue = _recurrenceIterator.next();

		CalendarBooking calendarBooking =
			CalendarBookingLocalServiceUtil.getCalendarBookingWithDate(
				_calendarBookings, _currentDateValue);

		CalendarBooking newCalendarBooking =
			(CalendarBooking)calendarBooking.clone();

		Calendar jCalendar = _getStartTimeJCalendar(
			_currentDateValue, calendarBooking);

		newCalendarBooking.setEndTime(
			jCalendar.getTimeInMillis() + calendarBooking.getDuration());
		newCalendarBooking.setInstanceIndex(_instanceIndex);
		newCalendarBooking.setStartTime(jCalendar.getTimeInMillis());

		_instanceIndex++;

		return newCalendarBooking;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	private Calendar _getStartTimeJCalendar(
		DateValue dateValue, CalendarBooking calendarBooking) {

		Calendar jCalendar = JCalendarUtil.getJCalendar(
			calendarBooking.getStartTime(), _getTimeZone(calendarBooking));

		Calendar startTimeJCalendar = JCalendarUtil.getJCalendar(
			dateValue.year(), dateValue.month() - 1, dateValue.day(),
			jCalendar.get(Calendar.HOUR_OF_DAY), jCalendar.get(Calendar.MINUTE),
			jCalendar.get(Calendar.SECOND), jCalendar.get(Calendar.MILLISECOND),
			_getTimeZone(calendarBooking));

		TimeZone timeZone = _getTimeZone(calendarBooking);

		int shift = JCalendarUtil.getDSTShift(
			jCalendar, startTimeJCalendar, timeZone);

		startTimeJCalendar.add(Calendar.MILLISECOND, shift);

		return startTimeJCalendar;
	}

	private TimeZone _getTimeZone(CalendarBooking calendarBooking) {
		try {
			if (calendarBooking.isAllDay()) {
				return TimeZone.getTimeZone(StringPool.UTC);
			}

			return calendarBooking.getTimeZone();
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn(e, e);
			}
		}

		return TimeZoneUtil.getDefault();
	}

	private boolean _isExceededCount() {
		Recurrence recurrence = _calendarBooking.getRecurrenceObj();

		if (recurrence == null) {
			return false;
		}

		int count = recurrence.getCount();

		if ((count != 0) && (_instanceIndex >= count)) {
			return true;
		}

		return false;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CalendarBookingIterator.class);

	private final CalendarBooking _calendarBooking;
	private final List<CalendarBooking> _calendarBookings;
	private DateValue _currentDateValue;
	private int _instanceIndex;
	private final RecurrenceIterator _recurrenceIterator;
	private final long _startTime;

}