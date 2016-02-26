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

/**
 * @author Michael Bowerman
 */
public class UpgradeCalendar extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws Exception {
		if (!hasTable("CalendarBooking")) {
			runSQL("")

		}
		else {
			if (!hasColumn("CalendarBooking", "recurringCalendarBookingId")) {
				runSQL(
					"alter table CalendarBooking add " +
					"recurringCalendarBookingId long null");
				runSQL(
					"update CalendarBooking set recurringCalendarBookingId = " +
					"calendarBookingId where recurringCalendarBookingId is " +
					"null or recurringCalendarBookingId = 0");
			}
			if (!hasColumn("CalendarBooking", "masterRecurrence")) {
				runSQL(
					"alter table CalendarBooking add masterRecurrence String " +
					"null");

				runSQL(
					"update CalendarBooking set masterRecurrence = " +
					"recurrence where masterRecurrence is null or " +
					"masterRecurrence = ''");
			}
	}

}