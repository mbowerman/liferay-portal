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

import com.liferay.portal.kernel.bean.PortalBeanLocatorUtil;
import com.liferay.portal.kernel.concurrent.ThrowableAwareRunnable;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.Projection;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Contact;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.Resource;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.ResourcePermission;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.RoleConstants;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.ContactLocalServiceUtil;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.service.ResourceLocalServiceUtil;
import com.liferay.portal.kernel.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.verify.model.VerifiableResourcedModel;
import com.liferay.portal.util.PortalInstances;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Raymond Augé
 * @author James Lefeu
 */
public class VerifyResourcePermissions extends VerifyProcess {

	public void verify(VerifiableResourcedModel... verifiableResourcedModels)
		throws Exception {

		long[] companyIds = PortalInstances.getCompanyIdsBySQL();

		for (long companyId : companyIds) {
			Role role = RoleLocalServiceUtil.getRole(
				companyId, RoleConstants.OWNER);

			List<VerifyResourcedModelRunnable> verifyResourcedModelRunnables =
				new ArrayList<>(verifiableResourcedModels.length);

			for (VerifiableResourcedModel verifiableResourcedModel :
					verifiableResourcedModels) {

				VerifyResourcedModelRunnable verifyResourcedModelRunnable =
					new VerifyResourcedModelRunnable(
						role, verifiableResourcedModel);

				verifyResourcedModelRunnables.add(verifyResourcedModelRunnable);
			}

			doVerify(verifyResourcedModelRunnables);

			verifyLayout(role);
		}
	}

	@Override
	protected void doVerify() throws Exception {
		Map<String, VerifiableResourcedModel> verifiableResourcedModelsMap =
			PortalBeanLocatorUtil.locate(VerifiableResourcedModel.class);

		Collection<VerifiableResourcedModel> verifiableResourcedModels =
			verifiableResourcedModelsMap.values();

		verify(
			verifiableResourcedModels.toArray(
				new VerifiableResourcedModel[
					verifiableResourcedModels.size()]));
	}

	protected void verifyLayout(Role role) throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			long companyId = role.getCompanyId();
			String layoutModelName = Layout.class.getName();

			List<String> actionIds =
				ResourceActionsUtil.getModelResourceActions(layoutModelName);

			List<String> defaultOwnerActions =
				ResourceActionsUtil.getModelResourceOwnerDefaultActions(
					layoutModelName);

			if (!defaultOwnerActions.isEmpty()) {
				Iterator<String> itr = actionIds.iterator();

				while (itr.hasNext()) {
					String actionId = itr.next();

					if (!defaultOwnerActions.contains(actionId)) {
						itr.remove();
					}
				}
			}

			ActionableDynamicQuery actionableDynamicQuery =
				LayoutLocalServiceUtil.getActionableDynamicQuery();

			actionableDynamicQuery.setAddCriteriaMethod(
				new ActionableDynamicQuery.AddCriteriaMethod() {

					@Override
					public void addCriteria(DynamicQuery dynamicQuery) {
						DynamicQuery resourcePermissionDynamicQuery =
							ResourcePermissionLocalServiceUtil.dynamicQuery();

						Property companyIdProperty =
							PropertyFactoryUtil.forName("companyId");
						Property nameProperty = PropertyFactoryUtil.forName(
							"name");
						Property roleIdProperty = PropertyFactoryUtil.forName(
							"roleId");

						resourcePermissionDynamicQuery.add(
							companyIdProperty.eq(role.getCompanyId()));
						resourcePermissionDynamicQuery.add(
							nameProperty.eq(layoutModelName));
						resourcePermissionDynamicQuery.add(
							roleIdProperty.eq(role.getRoleId()));

						Projection primKeyProjection =
							ProjectionFactoryUtil.property("primKey");

						resourcePermissionDynamicQuery.setProjection(
							primKeyProjection);

						Property plidProperty = PropertyFactoryUtil.forName(
							"plid");

						dynamicQuery.add(
							plidProperty.notIn(resourcePermissionDynamicQuery));

						Projection plidProjection =
							ProjectionFactoryUtil.property("plid");

						dynamicQuery.setProjection(plidProjection);
					}

				});

			int total = (int)actionableDynamicQuery.performCount();

			_verifyLayoutIndex = 0;

			actionableDynamicQuery.setPerformActionMethod(
				new ActionableDynamicQuery.PerformActionMethod() {

					@Override
					public void performAction(Object object)
						throws PortalException {

						_verifyLayoutIndex++;

						long primKey = (Long)object;

						if (_log.isInfoEnabled() &&
							(((_verifyLayoutIndex + 1) % 100) == 0)) {

							StringBundler sb = new StringBundler(9);

							sb.append("Processed ");
							sb.append(_verifyLayoutIndex + 1);
							sb.append(" of ");
							sb.append(total);
							sb.append(" resource permissions for company ");
							sb.append("= ");
							sb.append(companyId);
							sb.append(" and model ");
							sb.append(layoutModelName);

							_log.info(sb.toString());
						}

						if (_log.isDebugEnabled()) {
							StringBundler sb = new StringBundler(11);

							sb.append("No resource found for {");
							sb.append(companyId);
							sb.append(", ");
							sb.append(layoutModelName);
							sb.append(", ");
							sb.append(ResourceConstants.SCOPE_INDIVIDUAL);
							sb.append(", ");
							sb.append(primKey);
							sb.append(", ");
							sb.append(role.getRoleId());
							sb.append("}");

							_log.debug(sb.toString());
						}

						Resource resource =
							ResourceLocalServiceUtil.getResource(
								companyId, layoutModelName,
								ResourceConstants.SCOPE_INDIVIDUAL,
								String.valueOf(primKey));

						ResourcePermissionLocalServiceUtil.
							setOwnerResourcePermissions(
								resource.getCompanyId(), resource.getName(),
								resource.getScope(), resource.getPrimKey(),
								role.getRoleId(), 0,
								actionIds.toArray(
									new String[actionIds.size()]));
					}

				});

			actionableDynamicQuery.performActions();
		}
	}

	protected void verifyResourcedModel(
			long companyId, String modelName, long primKey, Role role,
			long ownerId, int cur, int total)
		throws Exception {

		if (_log.isInfoEnabled() && (((cur + 1) % 100) == 0)) {
			cur++;

			_log.info(
				"Processed " + cur + " of " + total + " resource permissions " +
					"for company = " + companyId + " and model " + modelName);
		}

		ResourcePermission resourcePermission =
			ResourcePermissionLocalServiceUtil.fetchResourcePermission(
				companyId, modelName, ResourceConstants.SCOPE_INDIVIDUAL,
				String.valueOf(primKey), role.getRoleId());

		if (resourcePermission == null) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"No resource found for {" + companyId + ", " + modelName +
						", " + ResourceConstants.SCOPE_INDIVIDUAL + ", " +
							primKey + ", " + role.getRoleId() + "}");
			}

			ResourceLocalServiceUtil.addResources(
				companyId, 0, ownerId, modelName, String.valueOf(primKey),
				false, false, false);
		}

		if (resourcePermission == null) {
			resourcePermission =
				ResourcePermissionLocalServiceUtil.fetchResourcePermission(
					companyId, modelName, ResourceConstants.SCOPE_INDIVIDUAL,
					String.valueOf(primKey), role.getRoleId());

			if (resourcePermission == null) {
				return;
			}
		}

		if (modelName.equals(User.class.getName())) {
			User user = UserLocalServiceUtil.fetchUserById(ownerId);

			if (user != null) {
				Contact contact = ContactLocalServiceUtil.fetchContact(
					user.getContactId());

				if (contact != null) {
					ownerId = contact.getUserId();
				}
			}
		}

		if (ownerId != resourcePermission.getOwnerId()) {
			resourcePermission.setOwnerId(ownerId);

			ResourcePermissionLocalServiceUtil.updateResourcePermission(
				resourcePermission);
		}
	}

	protected void verifyResourcedModel(
			Role role, VerifiableResourcedModel verifiableResourcedModel)
		throws Exception {

		int total = 0;

		try (LoggingTimer loggingTimer = new LoggingTimer(
				verifiableResourcedModel.getTableName());
			Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = con.prepareStatement(
				"select count(*) from " +
					verifiableResourcedModel.getTableName() +
						" where companyId = " + role.getCompanyId());
			ResultSet rs = ps.executeQuery()) {

			if (rs.next()) {
				total = rs.getInt(1);
			}
		}

		StringBundler sb = new StringBundler(8);

		sb.append("select ");
		sb.append(verifiableResourcedModel.getPrimaryKeyColumnName());
		sb.append(", ");
		sb.append(verifiableResourcedModel.getUserIdColumnName());
		sb.append(" from ");
		sb.append(verifiableResourcedModel.getTableName());
		sb.append(" where companyId = ");
		sb.append(role.getCompanyId());

		try (LoggingTimer loggingTimer = new LoggingTimer(
				verifiableResourcedModel.getTableName());
			Connection con = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = con.prepareStatement(sb.toString());
			ResultSet rs = ps.executeQuery()) {

			for (int i = 0; rs.next(); i++) {
				long primKey = rs.getLong(
					verifiableResourcedModel.getPrimaryKeyColumnName());
				long userId = rs.getLong(
					verifiableResourcedModel.getUserIdColumnName());

				verifyResourcedModel(
					role.getCompanyId(),
					verifiableResourcedModel.getModelName(), primKey, role,
					userId, i, total);
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		VerifyResourcePermissions.class);

	private static int _verifyLayoutIndex = 0;

	private class VerifyResourcedModelRunnable extends ThrowableAwareRunnable {

		public VerifyResourcedModelRunnable(
			Role role, VerifiableResourcedModel verifiableResourcedModel) {

			_role = role;
			_verifiableResourcedModel = verifiableResourcedModel;
		}

		@Override
		protected void doRun() throws Exception {
			verifyResourcedModel(_role, _verifiableResourcedModel);
		}

		private final Role _role;
		private final VerifiableResourcedModel _verifiableResourcedModel;

	}

}