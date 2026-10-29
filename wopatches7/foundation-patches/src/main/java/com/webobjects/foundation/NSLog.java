package com.webobjects.foundation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * This is a replacement for the original NSLog class since it has a dependency
 * on Apache log4j 1.x. That and it's replacement reload4j are not modular.
 */
public final class NSLog {
	public static final long DebugGroupEnterpriseObjects = 2L;

	public static final long DebugGroupWebObjects = 4L;

	public static final long DebugGroupApplicationGeneration = 8L;

	public static final long DebugGroupMultithreading = 16L;

	public static final long DebugGroupResources = 32L;

	public static final long DebugGroupArchiving = 64L;

	public static final long DebugGroupValidation = 128L;

	public static final long DebugGroupKeyValueCoding = 256L;

	public static final long DebugGroupComponentBindings = 512L;

	public static final long DebugGroupFormatting = 1024L;

	public static final long DebugGroupQualifiers = 2048L;

	public static final long DebugGroupIO = 8192L;

	public static final long DebugGroupTiming = 16384L;

	public static final long DebugGroupModel = 32768L;

	public static final long DebugGroupDatabaseAccess = 65536L;

	public static final long DebugGroupSQLGeneration = 131072L;

	public static final long DebugGroupUserInterface = 262144L;

	public static final long DebugGroupAssociations = 524288L;

	public static final long DebugGroupControllers = 1048576L;

	public static final long DebugGroupRules = 2097152L;

	public static final long DebugGroupDeployment = 4194304L;

	public static final long DebugGroupParsing = 8388608L;

	public static final long DebugGroupReflection = 16777216L;

	public static final long DebugGroupRequestHandling = 33554432L;

	public static final long DebugGroupComponents = 67108864L;

	public static final long DebugGroupJSPServlets = 134217728L;

	public static final long DebugGroupWebServices = 268435456L;

	private static final long _DebugGroupAll = -1L;

	private static final long _DefaultDebugGroups = -8388609L;

	public static final int DebugLevelOff = 0;

	public static final int DebugLevelCritical = 1;

	public static final int DebugLevelInformational = 2;

	public static final int DebugLevelDetailed = 3;

	public static final String _D2WTraceRuleFiringEnabledKey = "D2WTraceRuleFiringEnabled";

	public static final String _D2WTraceRuleModificationsEnabledKey = "D2WTraceRuleModificationsEnabled";

	private static final String EOAdaptorDebugEnabled = "EOAdaptorDebugEnabled";

	private static final String NSDebugGroups = "NSDebugGroups";

	private static final String NSDebugLevel = "NSDebugLevel";

	private static final int debugGroupMaxBitPos = 63;

	private static final int debugGroupMinBitPos = 0;

	private static final char debugGroupRangeChar = ':';

	private static final int notFound = -1;

	public static volatile Logger debug = new PrintStreamLogger(System.err);

	public static volatile Logger err = new PrintStreamLogger(System.err);

	public static volatile Logger out = new PrintStreamLogger(System.out);

	private static volatile long debugGroups = 0L;

	private static volatile boolean PRIVATE_DEBUGGING_ENABLED;

	private static volatile boolean _inInitPhase = false;

	public static volatile String _WODebuggingEnabledKey = "Undefined";

	static {
		out.setIsVerbose(false);
		debug.setIsVerbose(true);
		err.setIsVerbose(true);
		_initDebugDefaults();
	}

	public static void _conditionallyLogPrivateException(final Throwable t) {
		if (_debugLoggingAllowedForLevel(3)) {
			debug.appendln(t);
		}
	}

	public static boolean _debugLoggingAllowedForGroups(final long aDebugGroups) {
		return PRIVATE_DEBUGGING_ENABLED && debugLoggingAllowedForGroups(aDebugGroups);
	}

	public static boolean _debugLoggingAllowedForLevel(final int aDebugLevel) {
		return PRIVATE_DEBUGGING_ENABLED && debugLoggingAllowedForLevel(aDebugLevel);
	}

	public static boolean _debugLoggingAllowedForLevelAndGroups(final int aDebugLevel, final long aDebugGroups) {
		return PRIVATE_DEBUGGING_ENABLED && debugLoggingAllowedForLevelAndGroups(aDebugLevel, aDebugGroups);
	}

	private static void _initDebugDefaults() {
		try {
			String value = NSProperties.getProperty("NSPrivateDebuggingEnabled");
			PRIVATE_DEBUGGING_ENABLED = NSPropertyListSerialization.booleanForString(value);
			value = NSProperties.getProperty("NSDebugLevel");
			if (value != null) {
				final int parsedValue = parseIntValueFromString(value);
				setAllowedDebugLevel(parsedValue);
			}
			value = NSProperties.getProperty("EOAdaptorDebugEnabled");
			if (NSPropertyListSerialization.booleanForString(value)) {
				if (allowedDebugLevel() < 2) {
					setAllowedDebugLevel(2);
				}
				allowDebugLoggingForGroups(65536L);
			}
			value = NSProperties.getProperty(_WODebuggingEnabledKey);
			if (NSPropertyListSerialization.booleanForString(value)) {
				if (allowedDebugLevel() < 2) {
					setAllowedDebugLevel(2);
				}
				allowDebugLoggingForGroups(4L);
			}
			value = NSProperties.getProperty("D2WTraceRuleFiringEnabled");
			if (NSPropertyListSerialization.booleanForString(value)) {
				if (allowedDebugLevel() < 3) {
					setAllowedDebugLevel(3);
				}
				allowDebugLoggingForGroups(2097152L);
			}
			value = NSProperties.getProperty("D2WTraceRuleModificationsEnabled");
			if (NSPropertyListSerialization.booleanForString(value)) {
				if (allowedDebugLevel() < 3) {
					setAllowedDebugLevel(3);
				}
				allowDebugLoggingForGroups(8L);
			}
			value = NSProperties.getProperty("NSDebugGroups");
			if (value != null && value.length() > 0) {
				long parsedLongValue = 0L;
				Object plistValue = null;
				try {
					plistValue = NSPropertyListSerialization.propertyListFromString(value);
				} catch (final RuntimeException e) {
				}
				if (plistValue == null) {
					try {
						parsedLongValue = Long.parseLong(value);
					} catch (final NumberFormatException nfe) {
						err.appendln(
								"<NSLog> Unable to parse a property list from the following string -- using default instead!  String: "
										+ value);
					}
				} else if (plistValue instanceof String) {
					final String unparsedValue = (String) plistValue;
					if (unparsedValue.indexOf(':') == -1
							&& unparsedValue.toLowerCase().equals(unparsedValue.toUpperCase())) {
						try {
							parsedLongValue = Long.parseLong(unparsedValue);
						} catch (final NumberFormatException e) {
							err.appendln("<NSLog> Unable to parse a long value from the following string: \""
									+ unparsedValue + "\"");
						}
					} else {
						parsedLongValue = parseLongValueFromString(unparsedValue);
					}
				} else if (plistValue instanceof NSArray) {
					final NSArray debugGroupBitIDs = (NSArray) plistValue;
					final int count = debugGroupBitIDs.count();
					for (int i = 0; i < count; i++) {
						final Object debugGroupBitID = debugGroupBitIDs.objectAtIndex(i);
						if (debugGroupBitID instanceof String) {
							parsedLongValue |= parseLongValueFromString((String) debugGroupBitID);
						} else {
							err.appendln("<NSLog> Unable to parse an NSArray member of type '"
									+ debugGroupBitID.getClass().getName() + "' -- skipping that value!  member: "
									+ debugGroupBitID.toString());
						}
					}
				} else {
					err.appendln(
							"<NSLog> Unable to parse an NSArray or String from the following string -- using default instead!  String: "
									+ value);
				}
				if (parsedLongValue == 0L) {
					setAllowedDebugGroups(-8388609L);
				} else {
					setAllowedDebugGroups(parsedLongValue);
				}
			} else if (allowedDebugLevel() > 0 && debugGroups == 0L) {
				setAllowedDebugGroups(-8388609L);
			}
		} catch (final SecurityException exception) {
		}
	}

	private static int parseIntValueFromString(final String aDebugLevel) {
		int parsedIntValue = 0;
		if (aDebugLevel.charAt(0) >= '0' && aDebugLevel.charAt(0) <= '9') {
			try {
				parsedIntValue = Integer.parseInt(aDebugLevel);
			} catch (final NumberFormatException e) {
				err.appendln("<NSLog> Unable to parse Integer value from the following string -- skipping!  String: "
						+ aDebugLevel);
			}
		} else {
			final int delimiter = aDebugLevel.lastIndexOf('.');
			if (delimiter == -1) {
				err.appendln(
						"<NSLog> The given symbol is not in the form of [className].[fieldName] -- skipping!  String: "
								+ aDebugLevel);
			} else {
				final String className = aDebugLevel.substring(0, delimiter);
				final String fieldName = aDebugLevel.substring(delimiter + 1);
				final Class specifiedClass = _NSUtilities.classWithName(className);
				if (specifiedClass == null) {
					err.appendln("<NSLog> The given symbol does not indicate a loaded class -- skipping!  String: "
							+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
				} else {
					try {
						final Field specifiedField = specifiedClass.getField(fieldName);
						if (specifiedField.getType() == int.class) {
							parsedIntValue = specifiedField.getInt(null);
						} else {
							err.appendln(
									"<NSLog> The given symbol does not indicate an int value -- skipping!  String: "
											+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
						}
					} catch (final IllegalAccessException e) {
						err.appendln(
								"<NSLog> The underlying constructor for the specified class is inaccessible, perhaps because it isn't public -- skipping!  String: "
										+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
					} catch (final NullPointerException e) {
						err.appendln(
								"<NSLog> The specified field of the specified class is an instance field, not a class field -- skipping!  String: "
										+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
					} catch (final ExceptionInInitializerError e) {
						err.appendln(
								"<NSLog> The specified class failed during class initialization.  Perhaps the class has a circular dependency on NSLog itself, or else there's a general problem initializing that class at this time -- skipping!  String: "
										+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
					} catch (final NoSuchFieldException e) {
						err.appendln(
								"<NSLog> Unable to find the specified field for the loaded class, because it doesn't exist or because of Java security restrictions -- skipping!  String: "
										+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
					} catch (final SecurityException e) {
						err.appendln(
								"<NSLog> Unable to gather the specified field due to Java security restrictions -- skipping!  String: "
										+ aDebugLevel + "; class: " + className + "; field: " + fieldName);
					}
				}
			}
		}
		return parsedIntValue;
	}

	private static long parseLongValueFromString(final String aDebugGroup) {
		long parsedLongValue = 0L;
		final int rangeDelimiter = aDebugGroup.indexOf(':');
		if (rangeDelimiter == -1) {
			if (aDebugGroup.charAt(0) >= '0' && aDebugGroup.charAt(0) <= '9') {
				try {
					final int bitPosition = Integer.parseInt(aDebugGroup);
					if (bitPosition < 0 || bitPosition > 63) {
						err.appendln("<NSLog> Invalid literal bit position -- skipping!  String: " + aDebugGroup);
					} else {
						parsedLongValue = 1L << bitPosition;
					}
				} catch (final NumberFormatException e) {
					err.appendln(
							"<NSLog> Unable to parse Integer value from the following string -- skipping!  String: "
									+ aDebugGroup);
				}
			} else {
				final int delimiter = aDebugGroup.lastIndexOf('.');
				if (delimiter == -1) {
					err.appendln(
							"<NSLog> The given symbol is not in the form of [className].[fieldName] -- skipping!  String: "
									+ aDebugGroup);
				} else {
					final String className = aDebugGroup.substring(0, delimiter);
					final String fieldName = aDebugGroup.substring(delimiter + 1);
					final Class specifiedClass = _NSUtilities.classWithName(className);
					if (specifiedClass == null) {
						err.appendln("<NSLog> The given symbol does not indicate a loaded class -- skipping!  String: "
								+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
					} else {
						try {
							final Field specifiedField = specifiedClass.getField(fieldName);
							if (specifiedField.getType() == long.class) {
								parsedLongValue = specifiedField.getLong(null);
							} else {
								err.appendln(
										"<NSLog> The given symbol does not indicate a long value -- skipping!  String: "
												+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
							}
						} catch (final IllegalAccessException e) {
							err.appendln(
									"<NSLog> The underlying constructor for the specified class is inaccessible, perhaps because it isn't public -- skipping!  String: "
											+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
						} catch (final NullPointerException e) {
							err.appendln(
									"<NSLog> The specified field of the specified class is an instance field, not a class field -- skipping!  String: "
											+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
						} catch (final ExceptionInInitializerError e) {
							err.appendln(
									"<NSLog> The specified class failed during class initialization.  Perhaps the class has a circular dependency on NSLog itself, or else there's a general problem initializing that class at this time -- skipping!  String: "
											+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
						} catch (final NoSuchFieldException e) {
							err.appendln(
									"<NSLog> Unable to find the specified field for the loaded class, because it doesn't exist or because of Java security restrictions -- skipping!  String: "
											+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
						} catch (final SecurityException e) {
							err.appendln(
									"<NSLog> Unable to gather the specified field due to Java security restrictions -- skipping!  String: "
											+ aDebugGroup + "; class: " + className + "; field: " + fieldName);
						}
					}
				}
			}
		} else {
			int highEndOfRange = 63;
			int lowEndOfRange = 0;
			if (rangeDelimiter == 0) {
				final String literal = aDebugGroup.substring(1);
				try {
					highEndOfRange = Integer.parseInt(literal);
				} catch (final NumberFormatException e) {
					err.appendln(
							"<NSLog> Unable to parse Integer value from the following high string value -- skipping!  String: "
									+ aDebugGroup + "; high: " + literal);
					highEndOfRange = -1;
				}
			} else if (rangeDelimiter == aDebugGroup.length() - 1) {
				final String literal = aDebugGroup.substring(0, rangeDelimiter);
				try {
					lowEndOfRange = Integer.parseInt(literal);
				} catch (final NumberFormatException e) {
					err.appendln(
							"<NSLog> Unable to parse Integer value from the following low string value -- skipping!  String: "
									+ aDebugGroup + "; low: " + literal);
					lowEndOfRange = -1;
				}
			} else {
				final String literal1 = aDebugGroup.substring(0, rangeDelimiter);
				final String literal2 = aDebugGroup.substring(rangeDelimiter + 1);
				try {
					lowEndOfRange = Integer.parseInt(literal1);
					highEndOfRange = Integer.parseInt(literal2);
				} catch (final NumberFormatException e) {
					err.appendln(
							"<NSLog> Unable to parse Integer value from one or both of the following string values -- skipping!  String: "
									+ aDebugGroup + "; low: " + literal1 + "; high: " + literal2);
					lowEndOfRange = -1;
					highEndOfRange = -1;
				}
			}
			if (highEndOfRange != -1 && lowEndOfRange != -1) {
				if (highEndOfRange < lowEndOfRange) {
					err.appendln(
							"<NSLog> Invalid range: low end value is greater than the high end value -- skipping!  String: "
									+ aDebugGroup + "; low: " + lowEndOfRange + "; high: " + highEndOfRange);
				} else if (lowEndOfRange < 0 || lowEndOfRange > 63 || highEndOfRange < 0 || highEndOfRange > 63) {
					err.appendln(
							"<NSLog> One or both of these literal bit positions are invalid (must be in range 0-63) -- skipping!  String: "
									+ aDebugGroup + "; low: " + lowEndOfRange + "; high: " + highEndOfRange);
				} else {
					for (int i = lowEndOfRange; i <= highEndOfRange; i++) {
						parsedLongValue |= 1L << i;
					}
				}
			}
		}
		return parsedLongValue;
	}

	public static void _setInInitPhase(final boolean flag) {
		if (!flag && _inInitPhase) {
			_initDebugDefaults();
		}
		_inInitPhase = flag;
	}

	public static synchronized void allowDebugLoggingForGroups(final long aDebugGroups) {
		debugGroups |= aDebugGroups;
	}

	@Deprecated
	public static int allowedDebugLevel() {
		return debug.allowedDebugLevel();
	}

	public static boolean debugLoggingAllowedForGroups(final long aDebugGroups) {
		return _inInitPhase || debug.allowedDebugLevel() > 0 && (debugGroups & aDebugGroups) != 0L;
	}

	public static boolean debugLoggingAllowedForLevel(final int aDebugLevel) {
		return _inInitPhase && aDebugLevel <= 1 || aDebugLevel > 0 && aDebugLevel <= allowedDebugLevel();
	}

	public static boolean debugLoggingAllowedForLevelAndGroups(final int aDebugLevel, final long aDebugGroups) {
		return debugLoggingAllowedForLevel(aDebugLevel) && debugLoggingAllowedForGroups(aDebugGroups);
	}

	public static PrintStream printStreamForPath(final String aPath) {
		PrintStream aStream = null;
		if (aPath != null) {
			final File aFile = new File(aPath);
			try {
				aStream = new PrintStream(new FileOutputStream(aFile), true);
			} catch (final IOException e) {
			}
		}
		return aStream;
	}

	public static synchronized void refuseDebugLoggingForGroups(final long aDebugGroups) {
		debugGroups &= aDebugGroups ^ 0xFFFFFFFFFFFFFFFFL;
	}

	public static void setAllowedDebugGroups(final long aDebugGroups) {
		debugGroups = aDebugGroups;
	}

	@Deprecated
	public static void setAllowedDebugLevel(final int aDebugLevel) {
		debug.setAllowedDebugLevel(aDebugLevel);
	}

	public static void setDebug(final Logger instance) {
		if (instance != null) {
			debug = instance;
		}
	}

	public static void setDebug(final Logger instance, final int aDebugLevel) {
		if (instance != null) {
			instance.setAllowedDebugLevel(aDebugLevel);
			debug = instance;
		}
	}

	public static void setErr(final Logger instance) {
		if (instance != null) {
			err = instance;
		}
	}

	public static void setOut(final Logger instance) {
		if (instance != null) {
			out = instance;
		}
	}

	public static String throwableAsString(final Throwable t) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}

	public static abstract class Logger {
		protected int debugLevel = 0;

		protected boolean isEnabled = true;

		protected boolean isVerbose = true;

		public int allowedDebugLevel() {
			return debugLevel;
		}

		public void appendln(final boolean aValue) {
			appendln(aValue ? Boolean.TRUE : Boolean.FALSE);
		}

		public void appendln(final byte aValue) {
			appendln(Byte.valueOf(aValue));
		}

		public void appendln(final byte[] aValue) {
			appendln(new String(aValue));
		}

		public void appendln(final char aValue) {
			appendln(Character.valueOf(aValue));
		}

		public void appendln(final char[] aValue) {
			appendln(new String(aValue));
		}

		public void appendln(final double aValue) {
			appendln(Double.valueOf(aValue));
		}

		public void appendln(final float aValue) {
			appendln(Float.valueOf(aValue));
		}

		public void appendln(final int aValue) {
			appendln(_NSUtilities.IntegerForInt(aValue));
		}

		public void appendln(final long aValue) {
			appendln(Long.valueOf(aValue));
		}

		public void appendln(final short aValue) {
			appendln(Short.valueOf(aValue));
		}

		public void appendln(final Throwable aValue) {
			appendln(NSLog.throwableAsString(aValue));
		}

		public abstract void appendln(Object param1Object);

		public abstract void appendln();

		public abstract void flush();

		public boolean isEnabled() {
			return isEnabled;
		}

		public boolean isVerbose() {
			return isVerbose;
		}

		public void setAllowedDebugLevel(final int aDebugLevel) {
			if (aDebugLevel < 0 || aDebugLevel > 3) {
				throw new IllegalArgumentException(
						"<" + getClass().getName() + "> Invalid debug level: " + aDebugLevel);
			}
			debugLevel = aDebugLevel;
		}

		public void setIsEnabled(final boolean aBool) {
			isEnabled = aBool;
		}

		public void setIsVerbose(final boolean aBool) {
			isVerbose = aBool;
		}
	}

	/**
	 * maybe reimplement this with org.slf4j?
	 */
	public static class Log4JLogger extends Logger {

		public Log4JLogger() {
		}

		@Override
		public int allowedDebugLevel() {
			return 0;
		}

		@Override
		public void appendln() {
			appendln("");
		}

		@Override
		public void appendln(final Object aValue) {
		}

		@Override
		public void flush() {
		}

		@Override
		public void setAllowedDebugLevel(final int aDebugLevel) {
		}
	}

	public static class PrintStreamLogger extends Logger {
		protected String _prefixInfo = null;

		protected PrintStream _stream;

		private static final String _defaultFormatString = "yyyy-M-d H:m:s z";

		private static final TimeZone _tz;

		private long _lastVerboseLogTime;

		private String _lastTimestampText;

		static {
			try {
				_tz = TimeZone.getDefault();
			} catch (final Exception e) {
				System.err.println("unable to initialize NSLog");
				e.printStackTrace(System.err);
				throw new RuntimeException(e);
			}
		}

		public PrintStreamLogger() {
			this(System.out);
		}

		public PrintStreamLogger(final PrintStream ps) {
			if (ps == null) {
				throw new IllegalArgumentException(
						"<" + getClass().getName() + "> java.io.PrintStream argument must be non-null");
			}
			_stream = ps;
			_lastVerboseLogTime = NSTimestamp.DistantPast.getTime();
		}

		public void _setPrefixInfo(final String s) {
			_prefixInfo = s;
		}

		protected String _verbosePrefix() {
			final String threadName = Thread.currentThread().getName();
			final NSTimestamp now = new NSTimestamp();
			final long offset = now.getTime();
			final String prefixInfo = _prefixInfo;
			final StringBuilder sb = new StringBuilder(50);
			synchronized (this) {
				if (offset - _lastVerboseLogTime >= 1000L) {
					final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-M-d H:m:s z");
					formatter.setTimeZone(_tz);
					_lastTimestampText = formatter.format(now);
					_lastVerboseLogTime = offset;
				}
			}
			if (prefixInfo != null) {
				sb.append("<");
				sb.append(_prefixInfo);
				sb.append(">");
			}
			sb.append("[");
			sb.append(_lastTimestampText);
			sb.append("] <");
			sb.append(threadName);
			sb.append("> ");
			return sb.toString();
		}

		@Override
		public synchronized void appendln() {
			if (isEnabled) {
				synchronized (_stream) {
					_stream.println();
					_stream.flush();
				}
			}
		}

		@Override
		public synchronized void appendln(final Throwable aValue) {
			if (isEnabled) {
				synchronized (_stream) {
					final StringBuilder stackTrace = new StringBuilder();
					if (isVerbose) {
						stackTrace.append(_verbosePrefix());
					}
					stackTrace.append(NSLog.throwableAsString(aValue));
					_stream.println(stackTrace.toString());
					_stream.flush();
				}
			}
		}

		@Override
		public synchronized void appendln(final Object aValue) {
			if (isEnabled) {
				synchronized (_stream) {
					if (isVerbose) {
						_stream.println(_verbosePrefix() + aValue);
					} else {
						_stream.println(aValue);
					}
					_stream.flush();
				}
			}
		}

		@Override
		public void flush() {
			_stream.flush();
		}

		public PrintStream printStream() {
			return _stream;
		}

		public void setPrintStream(final PrintStream aStream) {
			if (aStream != null) {
				_stream = aStream;
			}
		}
	}

	public static class _DevNullPrintStream extends PrintStream {
		public OutputStream originalOutputStream = null;

		public _DevNullPrintStream(final OutputStream os) {
			super(os);
			originalOutputStream = os;
		}

		public _DevNullPrintStream(final OutputStream os, final boolean aBOOL) {
			super(os, aBOOL);
			originalOutputStream = os;
		}

		@Override
		protected void setError() {
		}

		@Override
		public boolean checkError() {
			return false;
		}

		@Override
		public void close() {
		}

		@Override
		public void flush() {
		}

		@Override
		public void print(final boolean b) {
		}

		@Override
		public void print(final char c) {
		}

		@Override
		public void print(final char[] s) {
		}

		@Override
		public void print(final double d) {
		}

		@Override
		public void print(final float f) {
		}

		@Override
		public void print(final int i) {
		}

		@Override
		public void print(final long l) {
		}

		@Override
		public void print(final Object obj) {
		}

		@Override
		public void print(final String s) {
		}

		@Override
		public void println() {
		}

		@Override
		public void println(final boolean x) {
		}

		@Override
		public void println(final char x) {
		}

		@Override
		public void println(final char[] x) {
		}

		@Override
		public void println(final double x) {
		}

		@Override
		public void println(final float x) {
		}

		@Override
		public void println(final int x) {
		}

		@Override
		public void println(final long x) {
		}

		@Override
		public void println(final Object x) {
		}

		@Override
		public void println(final String x) {
		}

		@Override
		public void write(final int b) {
		}

		@Override
		public void write(final byte[] buf, final int off, final int len) {
		}
	}
}