package com.webobjects.appserver.xml;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Stack;
import java.util.TimeZone;

import com.webobjects.eocontrol.EOClassDescription;
import com.webobjects.eocontrol.EOEnterpriseObject;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSRange;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation._NSMutableIntegerDictionary;
import com.webobjects.foundation._NSUtilities;

public class WOXMLCoder {
	protected StringBuffer _buffer;

	protected String xmlDeclaration = "";

	protected _IdentityCodeMap _idMap = new _IdentityCodeMap();

	private final NSMutableDictionary<Integer, Object> _encodedObjects = new NSMutableDictionary<>();

	private int refCount = 0;

	private int tabCount = 0;

	private static NSArray<String> tabArray = new NSArray<>(new String[] {
			"\t", "\t\t", "\t\t\t", "\t\t\t\t", "\t\t\t\t\t", "\t\t\t\t\t\t", "\t\t\t\t\t\t\t", "\t\t\t\t\t\t\t\t",
			"\t\t\t\t\t\t\t\t\t", "\t\t\t\t\t\t\t\t\t\t",
			"\t\t\t\t\t\t\t\t\t\t\t", "\t\t\t\t\t\t\t\t\t\t\t\t", "\t\t\t\t\t\t\t\t\t\t\t\t\t",
			"\t\t\t\t\t\t\t\t\t\t\t\t\t\t", "\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t" });

	protected Stack<String> _encodedClasses = new Stack<>();

	protected void cr() {
		final int n = _encodedClasses.size();
		_buffer.append('\n');
		for (int i = 0; i < n; i++) {
			_buffer.append("    ");
		}
	}

	protected boolean typeNeedsIndentation(final Object o) {
		return o != null && !(o instanceof String) && !(o instanceof Number) && !(o instanceof NSTimestamp)
				&& !(o instanceof Boolean);
	}

	protected String encodedClassName() {
		return _encodedClasses.peek();
	}

	protected String xmlTagForClassNamed(final String className) {
		return className;
	}

	protected String xmlTagForPropertyKey(final String propertyKey, final String className) {
		return propertyKey;
	}

	private String _tabString(final int aTabCount) {
		if (aTabCount == 0) {
			return "";
		}
		if (aTabCount < 16 && aTabCount > 0) {
			return tabArray.objectAtIndex(aTabCount - 1);
		}
		final StringBuffer aTabString = new StringBuffer("\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t");
		for (int i = 16; i < aTabCount; i++) {
			aTabString.append('\t');
		}
		return new String(aTabString);
	}

	void appendAttribute(final String name, final String value) {
		_buffer.append(' ');
		_buffer.append(name);
		_buffer.append("=\"");
		_buffer.append(value);
		_buffer.append('"');
	}

	void appendAttribute(final String name, final int value) {
		appendAttribute(name, Integer.toString(value));
	}

	void appendTypeAndObjectID(final String type) {
		appendAttribute("type", type);
		appendAttribute("objectID", refCount);
		_buffer.append('>');
	}

	void appendAndTab(final String toAppend) {
		_buffer.append(toAppend);
		tabCount++;
	}

	void beginNode(final String name) {
		if (!_isPlausibleName(name)) {
			throw new WOXMLException("'" + name + "' is not a plausible name for an XML tag");
		}
		_buffer.append(_tabString(tabCount));
		_buffer.append('<');
		_buffer.append(name);
	}

	void endNode(final String name) {
		_buffer.append('<');
		_buffer.append('/');
		_buffer.append(name);
		_buffer.append('>');
		_buffer.append('\n');
	}

	private static boolean _isPlausibleName(final String s) {
		if (s == null || s.length() == 0) {
			return false;
		}
		final char[] ca = s.toCharArray();
		char c = ca[0];
		if (!Character.isLetter(c) && c != '_' && c != ':') {
			return false;
		}
		for (int i = 1; i < ca.length; i++) {
			c = ca[i];
			if (!Character.isLetter(c) && !Character.isDigit(c) && c != '_' && c != ':' && c != '.' && c != '-'
					&& Character.getNumericValue(c) < 128) {
				return false;
			}
		}
		return true;
	}

	protected void encodeObjectInTag(final String s, final String xmlTag, final String type) {
		if (type == null) {
			throw new WOXMLException(
					"cannot encode '" + s + "' with the xml tag '" + xmlTag + "' because the class type is null");
		}
		beginNode(xmlTag);
		appendTypeAndObjectID(type);
		_buffer.append(s);
		endNode(xmlTag);
	}

	protected void encodeStringInTag(final String s, final String xmlTag, final String type) {
		if (type == null) {
			throw new WOXMLException(
					"cannot encode '" + s + "' with the xml tag '" + xmlTag + "' because the class type is null");
		}
		beginNode(xmlTag);
		appendAttribute("type", type);
		if (!"boolean".equals(type) && !"int".equals(type) && !"float".equals(type) && !"double".equals(type)) {
			appendAttribute("objectID", refCount);
		}
		_buffer.append('>');
		_buffer.append(s);
		endNode(xmlTag);
	}

	protected void encodeReferenceInTag(final int objectID, final String xmlTag, final String type) {
		beginNode(xmlTag);
		appendAttribute("type", type);
		appendAttribute("objectIDRef", objectID);
		_buffer.append('>');
		endNode(xmlTag);
	}

	protected void _encodeStringForKey(final String aString, final String aKey) {
		final String className = aString.getClass().getName();
		encodeStringInTag(escapeString(aString), aKey, className);
	}

	protected String escapeString(final String toValidate) {
		final int length = toValidate.length();
		final StringBuffer cleanString = new StringBuffer(length);
		for (int i = 0; i < length; i++) {
			final char currentChar = toValidate.charAt(i);
			switch (currentChar) {
			case '&':
				cleanString.append("&amp;");
				break;
			case '<':
				cleanString.append("&lt;");
				break;
			case '>':
				cleanString.append("&gt;");
				break;
			case '\'':
				cleanString.append("&apos;");
				break;
			case '"':
				cleanString.append("&quot;");
				break;
			default:
				cleanString.append(currentChar);
				break;
			}
		}
		return new String(cleanString);
	}

	protected void _encodeArrayForKey(final NSArray objects, final String aKey) {
		beginNode(aKey);
		String className = "com.webobjects.foundation.NSMutableArray";
		if (!objects.getClass().getName().equals(className)) {
			className = objects.classForCoder().getName();
		}
		appendTypeAndObjectID(className);
		appendAndTab("\n");
		final int count = objects.count();
		for (int i = 0; i < count; i++) {
			final Object anObject = objects.objectAtIndex(i);
			encodeObjectForKey(anObject, "element");
		}
		tabCount--;
		_buffer.append(_tabString(tabCount));
		endNode(aKey);
	}

	protected void _encodeDictionaryForKey(final NSDictionary aDictionary, final String aKey) {
		beginNode(aKey);
		String className = "com.webobjects.foundation.NSMutableDictionary";
		if (!aDictionary.getClass().getName().equals(className)) {
			className = aDictionary.classForCoder().getName();
		}
		appendTypeAndObjectID(className);
		appendAndTab("\n");
		final NSArray keys = aDictionary.allKeys();
		final int keyCount = keys.count();
		for (int i = 0; i < keyCount; i++) {
			final String key = (String) keys.objectAtIndex(i);
			final Object currentObject = aDictionary.objectForKey(key);
			encodeObjectForKey(currentObject, key);
		}
		tabCount--;
		_buffer.append(_tabString(tabCount));
		endNode(aKey);
	}

	protected void _encodeDataForKey(final NSData theData, final String aKey) {
		String className = "com.webobjects.foundation.NSMutableData";
		if (!theData.getClass().getName().equals(className)) {
			className = theData.classForCoder().getName();
		}
		encodeObjectInTag(Base64.getEncoder().encodeToString(theData.bytes(new NSRange(0, theData.length()))), aKey,
				className);
	}

	protected void _encodeDateForKey(final NSTimestamp aDate, final String aKey) {
		final SimpleDateFormat aDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS z");
		aDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		final String className = aDate.getClass().getName();
		encodeStringInTag(aDateFormat.format(aDate), aKey, className);
	}

	protected void _encodeNumberForKey(final Number aNumber, final String aKey) {
		final String className = aNumber.getClass().getName();
		encodeStringInTag(aNumber.toString(), aKey, className);
	}

	protected void _encodeBooleanForKey(final Boolean b, final String aKey) {
		final String className = b.getClass().getName();
		encodeStringInTag(b.toString(), aKey, className);
	}

	protected void _encodeNullForKey(final String aKey) {
		encodeStringInTag("null", aKey, "?");
	}

	protected void _encodeWOXMLCodingForKey(final WOXMLCoding object, final String key) {
		final String className = object.classForCoder().getName();
		beginNode(key);
		appendTypeAndObjectID(className);
		appendAndTab("\n");
		object.encodeWithWOXMLCoder(this);
		tabCount--;
		_buffer.append(_tabString(tabCount));
		endNode(key);
	}

	protected void _encodeEOEnterpriseObjectForKey(final EOEnterpriseObject eo, final String aKey) {
		final String className = eo.getClass().getName();
		final EOClassDescription classDescription = eo.classDescription();
		if (classDescription == null) {
			throw new WOXMLException(eo.getClass().getName()
					+ " must either implement the WOXMLCoding interface or contain a class description that is not null.");
		}
		final String entityName = classDescription.entityName();
		beginNode(entityName);
		appendTypeAndObjectID(className);
		appendAndTab("\n");
		final NSArray<String> attributeKeys = classDescription.attributeKeys();
		int count = attributeKeys.count();
		for (int i = 0; i < count; i++) {
			final String attributeKey = attributeKeys.objectAtIndex(i);
			final Object attributeValue = eo.valueForKey(attributeKey);
			if (attributeValue != null) {
				encodeObjectForKey(attributeValue, attributeKey);
			}
		}
		final NSArray<String> toOneRelationshipKeys = classDescription.toOneRelationshipKeys();
		count = toOneRelationshipKeys.count();
		for (int j = 0; j < count; j++) {
			final String attributeKey = toOneRelationshipKeys.objectAtIndex(j);
			beginNode(attributeKey);
			appendAndTab(" type=\"relationship\" relationshipType=\"to-one\">\n");
			final Object attributeValue = eo.valueForKey(attributeKey);
			if (attributeValue != null) {
				encodeObjectForKey(attributeValue, attributeKey);
			}
			tabCount--;
			_buffer.append(_tabString(tabCount));
			endNode(attributeKey);
		}
		final NSArray<String> toManyRelationshipKeys = classDescription.toManyRelationshipKeys();
		count = toManyRelationshipKeys.count();
		for (int k = 0; k < count; k++) {
			final String attributeKey = toManyRelationshipKeys.objectAtIndex(k);
			final Object attributeValue = eo.valueForKey(attributeKey);
			if (attributeValue != null) {
				encodeObjectForKey(attributeValue, attributeKey);
			}
		}
		tabCount--;
		_buffer.append(_tabString(tabCount));
		endNode(entityName);
	}

	public static WOXMLCoder coder() {
		return new WOXMLCoder();
	}

	public static WOXMLCoder coderWithMapping(final String mappingURL) {
		return new WOXMLMappingCoder(mappingURL);
	}

	public String xmlDeclaration() {
		return xmlDeclaration;
	}

	public synchronized void setXmlDeclaration() {
		setXmlDeclaration("1.0", null, null);
	}

	public synchronized void setXmlDeclaration(final String version, final String encoding, final String standalone) {
		try {
			Float.parseFloat(version);
		} catch (final NumberFormatException e) {
			throw new IllegalArgumentException("XML version has to be a number, e.g. '1.0'");
		}
		if (standalone != null && !"yes".equals(standalone) && !"no".equals(standalone)) {
			throw new IllegalArgumentException("\"standalone\" can be only either \"yes\" or \"no\"");
		}
		final StringBuilder buf = new StringBuilder(255);
		buf.append("<?xml version=\"");
		buf.append(version);
		buf.append('"');
		if (encoding != null) {
			buf.append(" encoding=\"");
			buf.append(encoding);
			buf.append('"');
		}
		if (standalone != null) {
			buf.append(" standalone=\"");
			buf.append(standalone);
			buf.append('"');
		}
		buf.append("?>\n");
		xmlDeclaration = buf.toString();
	}

	public synchronized String encodeRootObjectForKey(final Object object, final String key) {
		String result = null;
		if (object != null) {
			_buffer = new StringBuffer(1024);
			_buffer.append(xmlDeclaration);
			encodeObjectForKey(object, key);
			result = _buffer.toString();
			_buffer = null;
		}
		return result;
	}

	public void encodeObjectForKey(final Object object, final String key) {
		final int referenceInteger = _idMap.identityCode(object);
		final Integer integerKey = _NSUtilities.IntegerForInt(referenceInteger);
		refCount = referenceInteger;
		if (object != null) {
			if (_encodedObjects.objectForKey(integerKey) != null) {
				final String className = object.getClass().getName();
				if (object instanceof EOEnterpriseObject) {
					final String entityName = ((EOEnterpriseObject) object).entityName();
					encodeReferenceInTag(referenceInteger, entityName, className);
				} else {
					encodeReferenceInTag(referenceInteger, key, className);
				}
			} else {
				_encodedObjects.setObjectForKey(object, integerKey);
				if (object instanceof WOXMLCoding) {
					_encodeWOXMLCodingForKey((WOXMLCoding) object, key);
				} else if (object instanceof String) {
					_encodeStringForKey((String) object, key);
				} else if (object instanceof NSArray) {
					_encodeArrayForKey((NSArray) object, key);
				} else if (object instanceof NSDictionary) {
					_encodeDictionaryForKey((NSDictionary) object, key);
				} else if (object instanceof NSTimestamp) {
					_encodeDateForKey((NSTimestamp) object, key);
				} else if (object instanceof Number) {
					_encodeNumberForKey((Number) object, key);
				} else if (object instanceof EOEnterpriseObject) {
					_encodeEOEnterpriseObjectForKey((EOEnterpriseObject) object, key);
				} else if (object instanceof NSData) {
					_encodeDataForKey((NSData) object, key);
				} else if (object instanceof Boolean) {
					_encodeBooleanForKey((Boolean) object, key);
				} else {
					throw new WOXMLException(object.getClass().getName()
							+ " must either implement the WOXMLCoding interface or be a class that WOXMLCoder supports (See WOXMLCoder reference).");
				}
			}
		} else {
			_encodeNullForKey(key);
		}
	}

	public void encodeBooleanForKey(final boolean b, final String key) {
		encodeStringInTag(b ? "True" : "False", key, "boolean");
	}

	public void encodeIntForKey(final int i, final String key) {
		encodeStringInTag(Integer.toString(i), key, "int");
	}

	public void encodeFloatForKey(final float f, final String key) {
		encodeStringInTag(Float.toString(f), key, "float");
	}

	public void encodeDoubleForKey(final double d, final String key) {
		encodeStringInTag(Double.toString(d), key, "double");
	}

	private static class _IdentityCodeMap {
		private static final int _NULL = 0;

		private static final int _UNREGISTERED = Integer.MIN_VALUE;

		private final _NSMutableIntegerDictionary<Object> _objectToIntegerMap = new _NSMutableIntegerDictionary<>(1024);

		private int _nextInt = 1;

		public int identityCode(final Object object) {
			if (object == null) {
				return _NULL;
			}
			int code = _objectToIntegerMap.integerForKey(object);
			if (code == _UNREGISTERED) {
				_objectToIntegerMap.setIntegerForKey(_nextInt, object);
				code = _nextInt++;
			}
			return code;
		}
	}
}