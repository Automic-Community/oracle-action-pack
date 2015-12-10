package com.uc4.ara.feature.database;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileLock;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import com.uc4.ara.feature.AbstractFeature;
import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.FeatureUtil.MsgTypes;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;

/**
 * The Class SQLFeature sends an sql-string to a database and processes the
 * result. This class makes use of the oracle-db-tool.jar.config file.
 */
public class SQLFeature extends AbstractFeature {

	//Command line parameters
	private String sqlScriptName;
	private String jdbcConnectionString; 
	private String user;
	private String password;
	private String driverMainClass;
	private String driverJAR;
	private String scriptSeperator;
	private String passwordInStatement;
	private String separator;
	private int connectionInterval;
	private String tnsNamesORAFileName;
	
	//Content of the SQL script to execute
	private String sqlString;

	//Number of results returned by the execution
	private int rowCount = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.uc4.ara.feature.IFeature#run(java.lang.String[])
	 */
	@Override
	public int run(String[] args) throws Exception {
		parseArgs(args);
		return executeSQLScript();
	}

	private void parseArgs(String[] args) {
		for (int i = 0; i < args.length; i++) {

			if (i == (args.length - 1)) {
				separator = args[i];
			}
			if (args[i].trim().toLowerCase().equals("-script"))
				sqlScriptName = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-usr"))
				user = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-pwd"))
				password = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-jdbc_conn"))
				jdbcConnectionString = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-jar"))
				driverJAR = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-class"))
				driverMainClass = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-sep"))
				scriptSeperator = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-intrvl"))
				connectionInterval = Integer.parseInt(args[i + 1]);
			if (args[i].trim().toLowerCase().equals("-sqlpwd"))
				passwordInStatement = args[i + 1];
			if (args[i].trim().toLowerCase().equals("-tns"))
				tnsNamesORAFileName = args[i + 1];
		}
	}

	private int executeSQLScript() throws Exception {
		int returnCode = ErrorCodes.OK;
		
		FeatureUtil.logMsg("Connecting with the following URL: " + jdbcConnectionString);

		FeatureUtil.logMsg("Reading from file " + sqlScriptName + " ...");
		File scriptFile = new File(sqlScriptName);
		if (!FileUtil.verifyFileExists(scriptFile)) {
			FeatureUtil.logMsg("Scriptfile not existing");
			return ErrorCodes.ERROR;
		}
		
		RandomAccessFile raf = null;
		FileLock lock = null;
		try {
			raf = new RandomAccessFile(scriptFile, "rw");
			lock = FileUtil.lockFile(raf, 10, 500);
			if (lock == null || !lock.isValid()) {
				FeatureUtil.logMsg("File " + scriptFile.getCanonicalPath() + " is locked by another instance!", MsgTypes.ERROR);
				return ErrorCodes.ERROR;
			}
			
			sqlString = FileUtil.fileAsString(raf).trim();
		} finally {
			if (lock != null)
				lock.release();
			if (raf != null)
				raf.close();
		}
		
		FeatureUtil.logMsg("SQL Query: " + sqlString);

		Connection connection;
		if (driverJAR.trim().length() > 0) {
			FeatureUtil.logMsg("Loading JAR-File: " + driverJAR + " ...");
			if (!new File(driverJAR).exists()) {
				FeatureUtil.logMsg("JAR-File not existing");
				return ErrorCodes.ERROR;
			}
			URL[] urls = new URL[1];
			urls[0] = (new File(driverJAR)).toURI().toURL();
			URLClassLoader loader = URLClassLoader.newInstance(urls);

			FeatureUtil.logMsg("Loading Class from JAR-File: " + driverMainClass
					+ " ...");

			//check if tnsnames.ora should be used
			if (tnsNamesORAFileName != null) {
				//check if the tnsNamesORAFile exists
				File tnsNamesORAFile = new File(tnsNamesORAFileName);
				if (!tnsNamesORAFile.exists()) {
					FeatureUtil.logMsg("tnsNamesORA file (" + tnsNamesORAFileName + ") does not exist");
					return ErrorCodes.ERROR;
				}
				//load tnsNamesOra file
				System.setProperty("oracle.net.tns_admin", tnsNamesORAFile.getPath());
				FeatureUtil.logMsg("Using tnsNamesORA file (" + tnsNamesORAFileName + ")");
			}
			
			Driver theDriver = (Driver) Class.forName(driverMainClass, true, loader)
					.newInstance();
			MyJdbcDriver driver = new MyJdbcDriver();
			driver.setTheDriver(theDriver);
			DriverManager.registerDriver(driver);

		} else {
			FeatureUtil.logMsg("Loading Class from Classpath: " + driverMainClass
					+ " ...");
			Driver driver = (Driver) Class.forName(driverMainClass).newInstance();
			DriverManager.registerDriver(driver);
		}
		FeatureUtil.logMsg("Loading successful.");

		//set connection timeout
		if (connectionInterval>0)
			DriverManager.setLoginTimeout(connectionInterval);
		
		connection = DriverManager.getConnection(jdbcConnectionString, user, password);

		sqlString = sqlString.replaceAll("(?s)((['\"]).*?(?<!\\\\)\\2)|/\\*.*?\\*/|//.*?(\r?\n|$)|--.*?(\r?\n|$)", "$1$3$4");
		String[] sqlStrings = sqlString.split(scriptSeperator);

		Statement statement = connection.createStatement();
		for (String sqlStatement : sqlStrings) {

			if (sqlStatement.trim().equals(""))
				continue;

			FeatureUtil.logMsg("Statement: " + sqlStatement);

			while (sqlStatement.contains("@Password@")) {
				sqlStatement = sqlStatement.replace("@Password@",
					passwordInStatement);
			}
			try {
				statement.execute(sqlStatement);

				ResultSet rs = statement.getResultSet();
				if (rs != null) {
					FeatureUtil.logMsg("Processing Resultset ...");

					do {
						while (rs.next()) {
							printRow(rs, separator);
							rowCount++;
						}
					} while (statement.getMoreResults());
				}
				int updateCount = statement.getUpdateCount();
				if (updateCount > -1) {
					FeatureUtil.logMsg("Processing affected rows ...");
					FeatureUtil.logMsg(String.format("%d rows updated", updateCount));
					rowCount += updateCount;
				}
			} catch (SQLException sqlEx) {
				if (sqlEx.getMessage().toLowerCase().contains(" exist")
						|| sqlEx.getErrorCode() == 1920 || sqlEx.getErrorCode() == 1918) {
					returnCode = ErrorCodes.SQL_EXISTS_ERROR;
					FeatureUtil.logMsg("SQL return code: " + sqlEx.getErrorCode());			
				} else {
					throw sqlEx;
				}
			}
		}
		
		statement.close();
		
		FeatureUtil.logMsg("Affected Rows: " + rowCount);
		FeatureUtil.logMsg("done.");

		return returnCode;

	}
	
	/**
	 * Prints the row.
	 * 
	 * @param rs
	 *            the rs
	 * @param separator
	 *            the separator
	 * @throws Exception
	 *             the exception
	 */
	private void printRow(ResultSet rs, String separator) throws Exception {
		ResultSetMetaData rsmd = rs.getMetaData();
		StringBuilder sb = new StringBuilder();

		sb.append(rs.getRow());
		sb.append(separator);

		for (int i = 1; i <= rsmd.getColumnCount(); i++) {
			sb.append(rsmd.getColumnName(i) + "=" + rs.getString(i));
			if (i != rsmd.getColumnCount())
				sb.append(separator);
		}

		FeatureUtil.logMsg(sb.toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.uc4.ara.feature.IFeature#getMinParams()
	 */
	@Override
	public int getMinParams() {
		return 5;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.uc4.ara.feature.IFeature#getMaxParams()
	 */
	@Override
	public int getMaxParams() {
		return 25;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.uc4.ara.feature.IFeature#printUsage()
	 */
	@Override
	public void printUsage() {
		FeatureUtil.logMsg("database Command:");
		FeatureUtil
				.logMsg("SQLFeature -script <script_file.sql> -usr <username> -pwd <password> -jdbc_conn <jdbc:..> -jar myDriver.jar -class <oracle.jdbc.OracleDriver> -sep <;> -intrvl 10 -sqlpwd <mypwd> -tns <myTnsFolder>");
	}

}
