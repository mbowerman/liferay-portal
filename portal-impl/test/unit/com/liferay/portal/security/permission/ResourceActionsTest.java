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

package com.liferay.portal.security.permission;

import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.PortletLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.xml.UnsecureSAXReaderUtil;
import com.liferay.portal.model.impl.PortletImpl;
import com.liferay.portal.xml.SAXReaderImpl;
import com.liferay.registry.BasicRegistryImpl;
import com.liferay.registry.RegistryUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Michael Bowerman
 */
public class ResourceActionsTest {

	@Before
	public void setUp() {
		RegistryUtil.setRegistry(new BasicRegistryImpl());

		UnsecureSAXReaderUtil unsecureSAXReaderUtil =
			new UnsecureSAXReaderUtil();

		unsecureSAXReaderUtil.setSAXReader(new SAXReaderImpl());

		ResourceActionsUtil resourceActionsUtil = new ResourceActionsUtil();

		ResourceActionsImpl resourceActionsImpl = new ResourceActionsImpl();

		ReflectionTestUtil.setFieldValue(
			resourceActionsImpl, "portletLocalService",
			ProxyUtil.newProxyInstance(
				_classLoader, new Class<?>[] {PortletLocalService.class},
				new InvocationHandler() {

					@Override
					public Object invoke(
							Object proxy, Method method, Object[] args)
						throws Throwable {

						return new PortletImpl(_COMPANY_ID, (String)args[0]);
					}

				}));

		resourceActionsImpl.afterPropertiesSet();

		resourceActionsUtil.setResourceActions(resourceActionsImpl);
	}

	@Test
	public void testModelResourceActionsRemainInBag() throws Exception {
		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceActions =
			ResourceActionsUtil.getModelResourceActions(_MODEL_NAME_2);

		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_1"));
		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_1"));
		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_2"));
	}

	@Test
	public void testModelResourceGroupDefaultActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGroupDefaultActions =
			ResourceActionsUtil.getModelResourceGroupDefaultActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGroupDefaultActions.contains("MODEL_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGroupDefaultActions =
			ResourceActionsUtil.getModelResourceGroupDefaultActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGroupDefaultActions.contains("MODEL_TEST_ACTION_1"));
	}

	@Test
	public void testModelResourceGuestDefaultActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGuestDefaultActions =
			ResourceActionsUtil.getModelResourceGuestDefaultActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGuestDefaultActions.contains("MODEL_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGuestDefaultActions =
			ResourceActionsUtil.getModelResourceGuestDefaultActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGuestDefaultActions.contains("MODEL_TEST_ACTION_1"));
	}

	@Test
	public void testModelResourceGuestUnsupportedActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGuestUnsupportedActions =
			ResourceActionsUtil.getModelResourceGuestUnsupportedActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGuestUnsupportedActions.contains(
				"MODEL_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGuestUnsupportedActions =
			ResourceActionsUtil.getModelResourceGuestUnsupportedActions(
				_MODEL_NAME_2);

		Assert.assertTrue(
			modelResourceGuestUnsupportedActions.contains(
				"MODEL_TEST_ACTION_2"));
	}

	@Test
	public void testObsoleteModelResourceActionsGetRemoved() throws Exception {
		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceActions =
			ResourceActionsUtil.getModelResourceActions(_MODEL_NAME);

		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_1"));
		Assert.assertTrue(modelResourceActions.contains("MODEL_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME);

		Assert.assertFalse(
			modelResourceActions.contains("MODEL_TEST_ACTION_1"));
		Assert.assertFalse(
			modelResourceActions.contains("MODEL_TEST_ACTION_2"));
	}

	@Test
	public void testObsoleteModelResourceGroupDefaultActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGroupDefaultActions =
			ResourceActionsUtil.getModelResourceGroupDefaultActions(
				_MODEL_NAME);

		Assert.assertTrue(
			modelResourceGroupDefaultActions.contains("MODEL_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGroupDefaultActions =
			ResourceActionsUtil.getModelResourceGroupDefaultActions(
				_MODEL_NAME);

		Assert.assertFalse(
			modelResourceGroupDefaultActions.contains("MODEL_TEST_ACTION_1"));
	}

	@Test
	public void testObsoleteModelResourceGuestDefaultActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGuestDefaultActions =
			ResourceActionsUtil.getModelResourceGuestDefaultActions(
				_MODEL_NAME);

		Assert.assertTrue(
			modelResourceGuestDefaultActions.contains("MODEL_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGuestDefaultActions =
			ResourceActionsUtil.getModelResourceGuestDefaultActions(
				_MODEL_NAME);

		Assert.assertFalse(
			modelResourceGuestDefaultActions.contains("MODEL_TEST_ACTION_1"));
	}

	@Test
	public void testObsoleteModelResourceGuestUnsupportedActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> modelResourceGuestUnsupportedActions =
			ResourceActionsUtil.getModelResourceGuestUnsupportedActions(
				_MODEL_NAME);

		Assert.assertTrue(
			modelResourceGuestUnsupportedActions.contains(
				"MODEL_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		modelResourceGuestUnsupportedActions =
			ResourceActionsUtil.getModelResourceGuestUnsupportedActions(
				_MODEL_NAME);

		Assert.assertFalse(
			modelResourceGuestUnsupportedActions.contains(
				"MODEL_TEST_ACTION_2"));
	}

	@Test
	public void testObsoletePortletResourceActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceActions =
			ResourceActionsUtil.getPortletResourceActions(_PORTLET_NAME);

		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_1"));
		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME);

		Assert.assertFalse(
			portletResourceActions.contains("PORTLET_TEST_ACTION_1"));
		Assert.assertFalse(
			portletResourceActions.contains("PORTLET_TEST_ACTION_2"));
	}

	@Test
	public void testObsoletePortletResourceGroupDefaultActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGroupDefaultActions =
			ResourceActionsUtil.getPortletResourceGroupDefaultActions(
				_PORTLET_NAME);

		Assert.assertTrue(
			portletResourceGroupDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGroupDefaultActions =
			ResourceActionsUtil.getPortletResourceGroupDefaultActions(
				_PORTLET_NAME);

		Assert.assertFalse(
			portletResourceGroupDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));
	}

	@Test
	public void testObsoletePortletResourceGuestDefaultActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGuestDefaultActions =
			ResourceActionsUtil.getPortletResourceGuestDefaultActions(
				_PORTLET_NAME);

		Assert.assertTrue(
			portletResourceGuestDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGuestDefaultActions =
			ResourceActionsUtil.getPortletResourceGuestDefaultActions(
				_PORTLET_NAME);

		Assert.assertFalse(
			portletResourceGuestDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));
	}

	@Test
	public void testObsoletePortletResourceGuestUnsupportedActionsGetRemoved()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGuestUnsupportedActions =
			ResourceActionsUtil.getPortletResourceGuestUnsupportedActions(
				_PORTLET_NAME);

		Assert.assertTrue(
			portletResourceGuestUnsupportedActions.contains(
				"PORTLET_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGuestUnsupportedActions =
			ResourceActionsUtil.getPortletResourceGuestUnsupportedActions(
				_PORTLET_NAME);

		Assert.assertFalse(
			portletResourceGuestUnsupportedActions.contains(
				"PORTLET_TEST_ACTION_2"));
	}

	@Test
	public void testPortletResourceActionsRemainInBag() throws Exception {
		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceActions =
			ResourceActionsUtil.getPortletResourceActions(_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_1"));
		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_1"));
		Assert.assertTrue(
			portletResourceActions.contains("PORTLET_TEST_ACTION_2"));
	}

	@Test
	public void testPortletResourceGroupDefaultActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGroupDefaultActions =
			ResourceActionsUtil.getPortletResourceGroupDefaultActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGroupDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGroupDefaultActions =
			ResourceActionsUtil.getPortletResourceGroupDefaultActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGroupDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));
	}

	@Test
	public void testPortletResourceGuestDefaultActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGuestDefaultActions =
			ResourceActionsUtil.getPortletResourceGuestDefaultActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGuestDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGuestDefaultActions =
			ResourceActionsUtil.getPortletResourceGuestDefaultActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGuestDefaultActions.contains(
				"PORTLET_TEST_ACTION_1"));
	}

	@Test
	public void testPortletResourceGuestUnsupportedActionsRemainInBag()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceGuestUnsupportedActions =
			ResourceActionsUtil.getPortletResourceGuestUnsupportedActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGuestUnsupportedActions.contains(
				"PORTLET_TEST_ACTION_2"));

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default2.xml");

		portletResourceGuestUnsupportedActions =
			ResourceActionsUtil.getPortletResourceGuestUnsupportedActions(
				_PORTLET_NAME_2);

		Assert.assertTrue(
			portletResourceGuestUnsupportedActions.contains(
				"PORTLET_TEST_ACTION_2"));
	}

	@Test
	public void testReferencedModelsGetRemovedWhenRemovingPortlet()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceActions =
			ResourceActionsUtil.getPortletResourceActions(_PORTLET_NAME);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		List<String> modelResourceActions =
			ResourceActionsUtil.getModelResourceActions(_MODEL_NAME);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		ResourceActionsUtil.removePortlet(_PORTLET_NAME);

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME);

		Assert.assertTrue(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME);

		Assert.assertTrue(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertTrue(ListUtil.isEmpty(modelResourceActions));
	}

	@Test
	public void testReferencedPortletsGetRemovedWhenRemovingModel()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceActions =
			ResourceActionsUtil.getPortletResourceActions(_PORTLET_NAME);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		List<String> modelResourceActions =
			ResourceActionsUtil.getModelResourceActions(_MODEL_NAME);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		ResourceActionsUtil.removeModel(_MODEL_NAME);

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME);

		Assert.assertTrue(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME);

		Assert.assertTrue(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertTrue(ListUtil.isEmpty(modelResourceActions));
	}

	@Test
	public void testUnreferencedModelsRemainWhenRemovingPortlet()
		throws Exception {

		ResourceActionsUtil.read(
			null, _classLoader, _SOURCE_PATH + "default.xml");

		List<String> portletResourceActions =
			ResourceActionsUtil.getPortletResourceActions(_PORTLET_NAME);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		List<String> modelResourceActions =
			ResourceActionsUtil.getModelResourceActions(_MODEL_NAME);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		ResourceActionsUtil.removePortlet(_PORTLET_NAME_2);

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME);

		Assert.assertFalse(ListUtil.isEmpty(portletResourceActions));

		portletResourceActions = ResourceActionsUtil.getPortletResourceActions(
			_PORTLET_NAME_2);

		Assert.assertTrue(ListUtil.isEmpty(portletResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));

		modelResourceActions = ResourceActionsUtil.getModelResourceActions(
			_MODEL_NAME_2);

		Assert.assertFalse(ListUtil.isEmpty(modelResourceActions));
	}

	private static final long _COMPANY_ID = RandomTestUtil.randomLong();

	private static final String _MODEL_NAME =
		"com.liferay.test.portlet.TestModel";

	private static final String _MODEL_NAME_2 =
		"com.liferay.test.portlet.TestModel2";

	private static final String _PORTLET_NAME =
		"com_liferay_test_portlet_TestPortlet";

	private static final String _PORTLET_NAME_2 =
		"com_liferay_test_portlet_TestPortlet2";

	private static final String _SOURCE_PATH =
		"com/liferay/portal/security/permission/dependencies/";

	private static final ClassLoader _classLoader =
		ResourceActionsTest.class.getClassLoader();

}