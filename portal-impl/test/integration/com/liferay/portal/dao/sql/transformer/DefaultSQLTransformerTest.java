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

package com.liferay.portal.dao.sql.transformer;

import com.liferay.portal.dao.orm.common.SQLTransformer;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.UnsafeConsumer;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Bowerman
 */
public class DefaultSQLTransformerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@BeforeClass
	public static void setUpClass() throws Exception {
		_db = DBManagerUtil.getDB();

		_db.runSQL(
			"create table DefaultSQLTransformerTest1 (id LONG not null " +
				"primary key, string VARCHAR(255) null, long_ LONG null, " +
					"date_ DATE null)");
		_db.runSQL(
			"create table DefaultSQLTransformerTest2 (id LONG not null " +
				"primary key, string VARCHAR(255) null, long_ LONG null)");

		_db.runSQL(
			"insert into DefaultSQLTransformerTest1 (id, string, long_) " +
				"values (1, 'Hello World', 0)");
		_db.runSQL(
			"insert into DefaultSQLTransformerTest1 (id, string, long_) " +
				"values (2, 'HELLO-WORLD-1', -1)");
		_db.runSQL(
			"insert into DefaultSQLTransformerTest1 (id, string, long_) " +
				"values (3, '%Test', 6)");
		_db.runSQL(
			"insert into DefaultSQLTransformerTest1 (id, string, long_) " +
				"values (4, '', 255)");
		_db.runSQL(
			"insert into DefaultSQLTransformerTest2 (id, string, long_) " +
				"values (1, '123', 456)");
		_db.runSQL(
			"insert into DefaultSQLTransformerTest2 (id, string, long_) " +
				"values (2, '-654', -321)");
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		_db.runSQL("drop table DefaultSQLTransformerTest1");
		_db.runSQL("drop table DefaultSQLTransformerTest2");
	}

	@Test
	public void testBitAnd() throws Exception {
		_testSQLTransformerLogic(
			"select BITAND(3, long_) from DefaultSQLTransformerTest1 order " +
				"by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(0, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(3, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(2, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(3, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testCastLong() throws Exception {
		_testSQLTransformerLogic(
			"select CAST_LONG(string) from DefaultSQLTransformerTest2 order " +
				"by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(123, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(-654, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testCastText() throws Exception {
		_testSQLTransformerLogic(
			"select CAST_TEXT(long_) from DefaultSQLTransformerTest2 order " +
				"by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("456", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("-321", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testCrossJoin() throws Exception {
		StringBundler sb = new StringBundler(6);

		sb.append("select DefaultSQLTransformerTest1.string as string1, ");
		sb.append("DefaultSQLTransformerTest2.string as string2 from ");
		sb.append("DefaultSQLTransformerTest1 CROSS JOIN ");
		sb.append("DefaultSQLTransformerTest2 order by ");
		sb.append("DefaultSQLTransformerTest1.id,");
		sb.append("DefaultSQLTransformerTest2.id");

		_testSQLTransformerLogic(
			sb.toString(),
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"Hello World", resultSet.getString("string1"));
					Assert.assertEquals("123", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"Hello World", resultSet.getString("string1"));
					Assert.assertEquals("-654", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"HELLO-WORLD-1", resultSet.getString("string1"));
					Assert.assertEquals("123", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"HELLO-WORLD-1", resultSet.getString("string1"));
					Assert.assertEquals("-654", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"%Test", resultSet.getString("string1"));
					Assert.assertEquals("123", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"%Test", resultSet.getString("string1"));
					Assert.assertEquals("-654", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("", resultSet.getString("string1"));
					Assert.assertEquals("123", resultSet.getString("string2"));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("", resultSet.getString("string1"));
					Assert.assertEquals("-654", resultSet.getString("string2"));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testEscape() throws Exception {
		_testSQLTransformerLogic(
			"select id from DefaultSQLTransformerTest1 where string like " +
				"'\\%Test'",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(3, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
		_testSQLTransformerLogic(
			"select id from DefaultSQLTransformerTest1 where string like " +
				"'\\%T'",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testInstr() throws Exception {
		_testSQLTransformerLogic(
			"select INSTR(string, 'W') from DefaultSQLTransformerTest1 order " +
				"by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(7, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(7, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(0, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(0, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
		_testSQLTransformerLogic(
			"select INSTR(string, 'WORLD') from DefaultSQLTransformerTest1 " +
				"where id = 2",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(7, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
		_testSQLTransformerLogic(
			"select INSTR(string, 'l') from DefaultSQLTransformerTest1 where " +
				"id = 1",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(3, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testIntegerDiv() throws Exception {
		_testSQLTransformerLogic(
			"select INTEGER_DIV(long_, 4) from DefaultSQLTransformerTest2 " +
				"order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(114, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(-80, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testLikeNull() throws Exception {
		_testSQLTransformerLogic(
			"select id from DefaultSQLTransformerTest2 where string like null",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testLower() throws Exception {
		_testSQLTransformerLogic(
			"select LOWER(string) from DefaultSQLTransformerTest1 order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("hello world", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"hello-world-1", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("%test", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testMod() throws Exception {
		_testSQLTransformerLogic(
			"select MOD(long_, 5) from DefaultSQLTransformerTest1 where id " +
				"!= 2 order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(0, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(1, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(0, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testNegativeComparisonFunction() throws Exception {
		_testSQLTransformerLogic(
			"select id from DefaultSQLTransformerTest1 where long_ = -1",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(2, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testNotEqualsBlankString() throws Exception {
		_testSQLTransformerLogic(
			"select id from DefaultSQLTransformerTest1 where string != '' " +
				"order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(1, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(2, resultSet.getLong(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(3, resultSet.getLong(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testNullDate() throws Exception {
		_testSQLTransformerLogic(
			"(select date_ from DefaultSQLTransformerTest1) union all " +
				"(select [$NULL_DATE$] from DefaultSQLTransformerTest2)",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					for (int i = 0; i < 6; i++) {
						Assert.assertTrue(resultSet.next());

						Assert.assertNull(resultSet.getTimestamp(1));
					}

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testReplace() throws Exception {
		_testSQLTransformerLogic(
			"select REPLACE(string, 'H', 'J') from " +
				"DefaultSQLTransformerTest1 order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("Jello World", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"JELLO-WORLD-1", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("%Test", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});
		_testSQLTransformerLogic(
			"select REPLACE(string, 'l', 'he') from " +
				"DefaultSQLTransformerTest1 where id = 1",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"Heheheo Worhed", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});

		_testSQLTransformerLogic(
			"select REPLACE(string, 'llo', 'y there') from " +
				"DefaultSQLTransformerTest1 where id = 1",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals(
						"Hey there World", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	@Test
	public void testSubstr() throws Exception {
		_testSQLTransformerLogic(
			"select substr(string, 2, 3) from DefaultSQLTransformerTest1 " +
				"where id != 4 order by id",
			new UnsafeConsumer<ResultSet, SQLException>() {

				@Override
				public void accept(ResultSet resultSet) throws SQLException {
					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("ell", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("ELL", resultSet.getString(1));

					Assert.assertTrue(resultSet.next());

					Assert.assertEquals("Tes", resultSet.getString(1));

					Assert.assertFalse(resultSet.next());
				}

			});
	}

	private void _testSQLTransformerLogic(
			String sql, UnsafeConsumer<ResultSet, SQLException> assertConsumer)
		throws Exception {

		sql = SQLTransformer.transform(sql);

		try (Connection connection = DataAccess.getUpgradeOptimizedConnection();
			PreparedStatement ps = connection.prepareStatement(sql);
			ResultSet rs = ps.executeQuery()) {

			assertConsumer.accept(rs);
		}
	}

	private static DB _db;

}