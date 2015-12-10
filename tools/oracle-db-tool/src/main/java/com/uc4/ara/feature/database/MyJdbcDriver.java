package com.uc4.ara.feature.database;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class MyJdbcDriver implements Driver {

	private Driver theDriver;

	public Driver getTheDriver() {
		return theDriver;
	}

	public void setTheDriver(Driver theDriver) {
		this.theDriver = theDriver;
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return theDriver.acceptsURL(url);
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		return theDriver.connect(url, info);
	}

	@Override
	public int getMajorVersion() {
		return theDriver.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return theDriver.getMinorVersion();
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
			throws SQLException {
		return theDriver.getPropertyInfo(url, info);
	}

	@Override
	public boolean jdbcCompliant() {
		return theDriver.jdbcCompliant();
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return java.util.logging.Logger.getGlobal();
	}
}
