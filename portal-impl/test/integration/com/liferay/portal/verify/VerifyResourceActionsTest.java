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

package com.liferay.portal.verify;

import com.liferay.portal.kernel.model.ResourceAction;
import com.liferay.portal.kernel.service.ResourceActionLocalServiceUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.verify.test.BaseVerifyProcessTestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Bowerman
 */
public class VerifyResourceActionsTest extends BaseVerifyProcessTestCase {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		super.setUp();

		_resourceAction1 = ResourceActionLocalServiceUtil.addResourceAction(
			_NAME_1, _ACTION_ID_1, 2);

		_resourceAction2 = ResourceActionLocalServiceUtil.addResourceAction(
			_NAME_1, _ACTION_ID_2, 2);

		_resourceAction3 = ResourceActionLocalServiceUtil.addResourceAction(
			_NAME_1, _ACTION_ID_3, 2);

		_resourceAction4 = ResourceActionLocalServiceUtil.addResourceAction(
			_NAME_2, _ACTION_ID_1, 2);

		_resourceAction5 = ResourceActionLocalServiceUtil.addResourceAction(
			_NAME_2, _ACTION_ID_2, 4);

		ResourceActionLocalServiceUtil.checkResourceActions();
	}

	@Test
	public void testDeleteDuplicateBitwiseValuesOnResource() throws Throwable {
		assertEquals(_resourceAction1);
		assertEquals(_resourceAction2);
		assertEquals(_resourceAction3);
		assertEquals(_resourceAction4);
		assertEquals(_resourceAction5);

		doVerify();

		assertEquals(_resourceAction1);

		assertNull(_resourceAction2);
		assertNull(_resourceAction3);

		assertEquals(_resourceAction4);
		assertEquals(_resourceAction5);
	}

	protected void assertEquals(ResourceAction expectedResourceAction) {
		ResourceAction resourceAction =
			ResourceActionLocalServiceUtil.fetchResourceAction(
				expectedResourceAction.getName(),
				expectedResourceAction.getActionId());

		Assert.assertEquals(expectedResourceAction, resourceAction);
	}

	protected void assertNull(ResourceAction removedResourceAction) {
		ResourceAction resourceAction =
			ResourceActionLocalServiceUtil.fetchResourceAction(
				removedResourceAction.getName(),
				removedResourceAction.getActionId());

		Assert.assertNull(resourceAction);
	}

	@Override
	protected VerifyProcess getVerifyProcess() {
		return new VerifyResourceActions();
	}

	private static final String _ACTION_ID_1 = "action1";

	private static final String _ACTION_ID_2 = "action2";

	private static final String _ACTION_ID_3 = "action3";

	private static final String _NAME_1 = "portlet1";

	private static final String _NAME_2 = "portlet2";

	@DeleteAfterTestRun
	private ResourceAction _resourceAction1;

	private ResourceAction _resourceAction2;
	private ResourceAction _resourceAction3;

	@DeleteAfterTestRun
	private ResourceAction _resourceAction4;

	@DeleteAfterTestRun
	private ResourceAction _resourceAction5;

}