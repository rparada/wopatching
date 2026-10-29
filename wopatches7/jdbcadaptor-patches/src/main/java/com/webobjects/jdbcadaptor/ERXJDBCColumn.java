package com.webobjects.jdbcadaptor;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.webobjects.eoaccess.EOAttribute;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation._NSUtilities;

/**
 * Adds numerical constant support to EOF. See ERXConstant for more info.
 *
 * @author ak
 *
 */
public class ERXJDBCColumn extends JDBCColumn {

	private String _constantClassName;
	private static final String NO_NAME = "no name";

	public ERXJDBCColumn(final EOAttribute attribute, final JDBCChannel channel, final int column, final ResultSet rs) {
		super(attribute, channel, column, rs);
	}

	public ERXJDBCColumn(final JDBCChannel aChannel) {
		super(aChannel);
	}

	@Override
	public void takeInputValue(final Object arg0, final int arg1, final boolean arg2) {
		try {
			super.takeInputValue(arg0, arg1, arg2);
		} catch (final NSForwardException ex) {
			if (ex.originalException() instanceof NoSuchMethodException) {
				final Class clazz = _NSUtilities.classWithName(_attribute.className());
				if (ERXConstant.Constant.class.isAssignableFrom(clazz)) {
					final Object value = ERXConstant.constantForClassNamed(arg0, _attribute.className());
					super.takeInputValue(value, arg1, arg2);
					return;
				}
			}
			throw ex;
		}
	}

	@Override
	Object _fetchValue(final boolean flag) {
		if (_rs == null || _column < 1) {
			throw new JDBCAdaptorException(" *** JDBCColumn : trying to fetchValue on a null ResultSet [" + _rs
					+ "] or unknow col [" + _column + "]!!", null);
		}
		if (_adaptorValueType == 0) {
			if (_constantClassName == null) {
				if (_attribute.userInfo() != null) {
					_constantClassName = (String) _attribute.userInfo().objectForKey("ERXConstantClassName");
				}
				if (_constantClassName == null) {
					_constantClassName = NO_NAME;
				}
			}
			if (_constantClassName != NO_NAME) {
				try {
					final int i = _rs.getInt(_column);
					if (_rs.wasNull()) {
						return NSKeyValueCoding.NullValue;
					}
					return ERXConstant.NumberConstant.constantForClassNamed(i, _constantClassName);
				} catch (final SQLException e) {
					throw new JDBCAdaptorException("Can't read constant: " + _constantClassName, e);
				}
			}
		}
		try {
			return super._fetchValue(flag);
		} catch (final NSForwardException ex) {
			NSLog.out.appendln("There's an error with this attribute: " + _attribute);
			throw ex;
		}
	}
}