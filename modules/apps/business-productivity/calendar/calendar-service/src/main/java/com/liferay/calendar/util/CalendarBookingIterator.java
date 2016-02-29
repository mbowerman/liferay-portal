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

	public CalendarBookingIterator(List<CalendarBooking> calendarBookings)
		throws ParseException {

		_calendarBookings = calendarBookings;

		CalendarBooking earliestCalendarBooking = _getEarliestCalendarBooking();

		_recurrenceIterator =
			RecurrenceIteratorFactory.createRecurrenceIterator(
				_getMasterRecurrence(),
				_toDateValue(earliestCalendarBooking.getStartTime()),
				earliestCalendarBooking.getTimeZone());
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

		CalendarBooking calendarBooking = _getCalendarBookingWithCurrentDate();

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

	private CalendarBooking _getCalendarBookingWithCurrentDate() {
		for (CalendarBooking calendarBooking : _calendarBookings) {
			if (_hasCurrentDateValue(calendarBooking)) {
				return calendarBooking;
			}
		}

		return null;
	}

	private CalendarBooking _getEarliestCalendarBooking() {
		CalendarBooking earliestCalendarBooking = _calendarBookings.get(0);

		for (CalendarBooking calendarBooking : _calendarBookings) {
			if (earliestCalendarBooking.getStartTime() >
					calendarBooking.getStartTime()) {

				earliestCalendarBooking = calendarBooking;
			}
		}

		return earliestCalendarBooking;
	}

	private String _getMasterRecurrence() {
		CalendarBooking calendarBooking = _calendarBookings.get(0);

		return calendarBooking.getMasterRecurrence();
	}

	private Recurrence _getMasterRecurrenceObj() {
		CalendarBooking calendarBooking = _calendarBookings.get(0);

		return calendarBooking.getMasterRecurrenceObj();
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

	private boolean _hasCurrentDateValue(CalendarBooking calendarBooking) {
		long startTime = calendarBooking.getStartTime();

		DateValue startDateValue = _toDateValue(startTime);

		Recurrence recurrence = calendarBooking.getRecurrenceObj();

		if (recurrence == null) {
			if (_currentDateValue.equals(startDateValue)) {
				return true;
			}
			else {
				return false;
			}
		}

		if (_currentDateValue.compareTo(startDateValue) < 0) {
			return false;
		}

		if (_hasCurrentExceptionDate(recurrence)) {
			return false;
		}

		Calendar untilJCalendar = recurrence.getUntilJCalendar();

		if (untilJCalendar == null) {
			return true;
		}

		DateValue untilDateValue = _toDateValue(untilJCalendar);

		if (_currentDateValue.compareTo(untilDateValue) > 0) {
			return false;
		}
		else {
			return true;
		}
	}

	private boolean _hasCurrentExceptionDate(Recurrence recurrence) {
		List<Calendar> exceptionJCalendars =
			recurrence.getExceptionJCalendars();

		List<DateValue> exceptionDateValues = new ArrayList<>();

		for (Calendar exceptionJCalendar : exceptionJCalendars) {
			exceptionDateValues.add(_toDateValue(exceptionJCalendar));
		}

		if (exceptionDateValues.contains(_currentDateValue)) {
			return true;
		}
		else {
			return false;
		}
	}

	private boolean _isExceededCount() {
		Recurrence recurrence = _getMasterRecurrenceObj();

		if (recurrence == null) {
			return false;
		}

		int count = recurrence.getCount();

		if ((count != 0) && (_instanceIndex >= count)) {
			return true;
		}

		return false;
	}

	private DateValue _toDateValue(Calendar jCalendar) {
		return new DateValueImpl(
			jCalendar.get(Calendar.YEAR), jCalendar.get(Calendar.MONTH) + 1,
			jCalendar.get(Calendar.DAY_OF_MONTH));
	}

	private DateValue _toDateValue(long time) {
		CalendarBooking calendarBooking = _calendarBookings.get(0);

		Calendar jCalendar = JCalendarUtil.getJCalendar(
			time, _getTimeZone(calendarBooking));

		return _toDateValue(jCalendar);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CalendarBookingIterator.class);

	private final List<CalendarBooking> _calendarBookings;
	private DateValue _currentDateValue;
	private int _instanceIndex;
	private final RecurrenceIterator _recurrenceIterator;

}