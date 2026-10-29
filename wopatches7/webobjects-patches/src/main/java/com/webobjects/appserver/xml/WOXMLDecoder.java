package com.webobjects.appserver.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Stack;
import java.util.TimeZone;

import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.webobjects.appserver.xml._private._DecodingContentHandler;
import com.webobjects.appserver.xml._private._DecodingHandler;
import com.webobjects.appserver.xml._private._DecodingNode;
import com.webobjects.appserver.xml._private._WOXMLMappingDecoder;
import com.webobjects.eocontrol.EOClassDescription;
import com.webobjects.eocontrol.EOEnterpriseObject;
import com.webobjects.eocontrol._EOCheapCopyMutableArray;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableData;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation._NSUtilities;

public class WOXMLDecoder implements NSKeyValueCoding {
	private Stack<_DecodingNode> _decodingStack;

	private boolean _useTopOfStack;

	private final NSMutableDictionary _decodedObjectCache = new NSMutableDictionary();

	@Deprecated
	private _DecodingHandler _handler;

	private _DecodingContentHandler _contentHandler;

	private String _parserClassName = "org.apache.xerces.parsers.SAXParser";

	@Deprecated
	private Parser _parser = null;

	private XMLReader _xmlReader = null;

	private String _encoding;

	@Override
	public String toString() {
		return "<" + getClass().getName() + " decodingStack="
				+ (_decodingStack == null ? "null" : _decodingStack.toString()) + " useTopOfStack=" + _useTopOfStack
				+ " decodedObjectCache=" + _decodedObjectCache + " decodingHandler="
				+ (_contentHandler == null ? "null" : _contentHandler.toString()) + " _xmlReader="
				+ (_xmlReader == null ? "null" : _xmlReader.toString()) + ">";
	}

	public void _setDecodingStack(final Stack<_DecodingNode> aStack) {
		_decodingStack = aStack;
	}

	public void _setUseTopOfStack(final boolean yn) {
		_useTopOfStack = yn;
	}

	private _DecodingNode _elementForKey(final String aKey) {
		final _DecodingNode currentNode = _decodingStack.peek();
		final NSArray theChildren = currentNode.getChildren();
		final int count = theChildren.count();
		final NSMutableArray matchingChildren = new NSMutableArray(count);
		for (int i = 0; i < count; i++) {
			final _DecodingNode aNode = (_DecodingNode) theChildren.objectAtIndex(i);
			if (aNode.getTagName().equals(aKey)) {
				matchingChildren.addObject(aNode);
			}
		}
		return (_DecodingNode) matchingChildren.objectAtIndex(0);
	}

	private Object objectFromCache(final _DecodingNode childNode) {
		Object anObject = null;
		final String objectIDRef = childNode.getObjectIDRef();
		if (objectIDRef != null) {
			anObject = _decodedObjectCache.objectForKey(objectIDRef);
		}
		return anObject;
	}

	protected void addObjectToCache(final _DecodingNode childNode, final Object anObject) {
		final String objectID = childNode.getObjectID();
		if (objectID == null) {
			throw new WOXMLException("Missing objectID");
		}
		_decodedObjectCache.setObjectForKey(anObject, objectID);
	}

	private Object constructObject(final _DecodingNode childNode, final Class aClass, final Class[] parameterTypes,
			final Object[] constructorArgs) {
		Object anObject = null;
		try {
			final Constructor aConstructor = aClass.getConstructor(parameterTypes);
			anObject = aConstructor.newInstance(constructorArgs);
			addObjectToCache(childNode, anObject);
		} catch (final NoSuchMethodException e) {
			final StringBuilder paramTypes = new StringBuilder();
			for (final Class parameterType : parameterTypes) {
				paramTypes.append(", ").append(parameterType);
			}
			throw new WOXMLException(e,
					": Missing constructor " + aClass.getName() + "(" + paramTypes.append(").").toString());
		} catch (final Exception e) {
			throw new WOXMLException(e.getMessage() + ":Unable to decode " + aClass.getName());
		}
		return anObject;
	}

	private Object constructFromNodeContent(final _DecodingNode childNode, final Class aClass) {
		Object anObject = objectFromCache(childNode);
		if (anObject == null) {
			anObject = constructObject(childNode, aClass, new Class[] { String.class },
					new Object[] { childNode.getContent() });
		}
		return anObject;
	}

	private Object _decodeNumber(final _DecodingNode childNode, final Class aClass) {
		return constructFromNodeContent(childNode, aClass);
	}

	private Object _decodeData(final _DecodingNode childNode, final Class<?> aClass) {
		Object anObject = null;
		final String aValue = childNode.getContent();
		final String objectIDRef = childNode.getObjectIDRef();
		if (objectIDRef != null) {
			final Object _anObject = _decodedObjectCache.objectForKey(objectIDRef);
			if (NSMutableData.class.isAssignableFrom(_anObject.getClass())) {
				if (NSMutableData.class.isAssignableFrom(aClass)) {
					anObject = _anObject;
				} else {
					anObject = new NSData((NSData) _anObject);
				}
			} else if (NSMutableData.class.isAssignableFrom(aClass)) {
				anObject = new NSMutableData((NSData) _anObject);
			} else {
				anObject = _anObject;
			}
		} else {
			final NSData data = new NSData(Base64.getDecoder().decode(aValue));
			if (NSMutableData.class.isAssignableFrom(aClass)) {
				anObject = new NSMutableData(data);
			} else {
				anObject = data;
			}
			final String objectID = childNode.getObjectID();
			_decodedObjectCache.setObjectForKey(anObject, objectID);
		}
		return anObject;
	}

	protected Object _decodeString(final _DecodingNode childNode, final Class aClass) {
		return constructFromNodeContent(childNode, aClass);
	}

	private Object _decodeBoolean(final _DecodingNode childNode, final Class aClass) {
		return constructFromNodeContent(childNode, aClass);
	}

	private Object _decodeDate(final _DecodingNode childNode, final Class aClass) {
		Object anObject = objectFromCache(childNode);
		if (anObject == null) {
			final String aValue = childNode.getContent();
			final SimpleDateFormat aDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS z");
			aDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			try {
				anObject = new NSTimestamp(aDateFormat.parse(aValue));
				addObjectToCache(childNode, anObject);
			} catch (final Exception e) {
				throw new WOXMLException(e, ":Unable to decode Date");
			}
		}
		return anObject;
	}

	private Object _decodeArray(final _DecodingNode childNode, final Class<?> aClass) {
		Object anObject = null;
		final String objectIDRef = childNode.getObjectIDRef();
		if (objectIDRef != null) {
			final Object _anObject = _decodedObjectCache.objectForKey(objectIDRef);
			if (NSMutableArray.class.isAssignableFrom(_anObject.getClass())) {
				if (NSMutableArray.class.isAssignableFrom(aClass)) {
					anObject = _anObject;
				} else {
					anObject = ((NSArray) _anObject).immutableClone();
				}
			} else if (NSMutableArray.class.isAssignableFrom(aClass)) {
				anObject = ((NSArray) _anObject).mutableClone();
			} else {
				anObject = _anObject;
			}
		} else {
			try {
				final NSArray grandChildrenNodes = childNode.getChildren();
				final int count = grandChildrenNodes.count();
				final Object[] constructorArgs = new Object[1];
				final Object[] _constructorArgs = new Object[count];
				constructorArgs[0] = _constructorArgs;
				for (int i = 0; i < count; i++) {
					final _DecodingNode grandChildNode = (_DecodingNode) grandChildrenNodes.objectAtIndex(i);
					_decodingStack.push(grandChildNode);
					_useTopOfStack = true;
					final Object grandChildObject = decodeObjectForKey("element");
					_useTopOfStack = false;
					_constructorArgs[i] = grandChildObject;
					_decodingStack.pop();
				}
				anObject = constructObject(childNode, aClass, new Class[] { Object[].class }, constructorArgs);
			} catch (final Exception e) {
				throw new WOXMLException(e, ":Unable to decode Array");
			}
		}
		return anObject;
	}

	private Object _decodeDictionary(final _DecodingNode childNode, final Class<?> aClass) {
		Object anObject = null;
		final String objectIDRef = childNode.getObjectIDRef();
		if (objectIDRef != null) {
			final Object _anObject = _decodedObjectCache.objectForKey(objectIDRef);
			if (NSMutableDictionary.class.isAssignableFrom(_anObject.getClass())) {
				if (NSMutableDictionary.class.isAssignableFrom(aClass)) {
					anObject = _anObject;
				} else {
					anObject = ((NSDictionary) _anObject).immutableClone();
				}
			} else if (NSMutableDictionary.class.isAssignableFrom(aClass)) {
				anObject = ((NSDictionary) _anObject).mutableClone();
			} else {
				anObject = _anObject;
			}
		} else {
			childNode.getObjectID();
			try {
				final NSArray grandChildrenNodes = childNode.getChildren();
				final int count = grandChildrenNodes.count();
				final Object[] constructorArgs = new Object[2];
				final Object[] _keys = new Object[count];
				final Object[] _values = new Object[count];
				constructorArgs[0] = _values;
				constructorArgs[1] = _keys;
				for (int i = 0; i < count; i++) {
					final _DecodingNode grandChildNode = (_DecodingNode) grandChildrenNodes.objectAtIndex(i);
					_decodingStack.push(grandChildNode);
					_useTopOfStack = true;
					final String _key = grandChildNode.getTagName();
					final Object grandChildObject = decodeObjectForKey(_key);
					_useTopOfStack = false;
					_keys[i] = _key;
					_values[i] = grandChildObject;
					_decodingStack.pop();
				}
				anObject = constructObject(childNode, aClass, new Class[] { Object[].class, Object[].class },
						constructorArgs);
			} catch (final Exception e) {
				throw new WOXMLException(e, ":Unable to decode Dictionary");
			}
		}
		return anObject;
	}

	private Object _decodeEO(final _DecodingNode childNode, final Class aClass) {
		Object anObject = null;
		try {
			final String entityName = childNode.getTagName();
			final String objectIDRef = childNode.getObjectIDRef();
			if (objectIDRef != null) {
				anObject = _decodedObjectCache.objectForKey(objectIDRef);
			} else {
				final EOClassDescription description = EOClassDescription.classDescriptionForEntityName(entityName);
				anObject = description.createInstanceWithEditingContext(null, null);
				final String objectID = childNode.getObjectID();
				_decodedObjectCache.setObjectForKey(anObject, objectID);
				final NSArray grandChildrenNodes = childNode.getChildren();
				final int count = grandChildrenNodes.count();
				for (int i = 0; i < count; i++) {
					final _DecodingNode grandChildNode = (_DecodingNode) grandChildrenNodes.objectAtIndex(i);
					if (grandChildNode.getChildren().count() > 0) {
						_decodingStack.push(grandChildNode);
						_useTopOfStack = true;
						final String _key = grandChildNode.getTagName();
						final Object grandChildObject = decodeObjectForKey(_key);
						((EOEnterpriseObject) anObject).validateTakeValueForKeyPath(grandChildObject, _key);
						_decodingStack.pop();
					} else if (grandChildNode.getContent() == null) {
						final Object grandChildObject = NSKeyValueCoding.NullValue;
					} else {
						_decodingStack.push(grandChildNode);
						_useTopOfStack = true;
						final String _key = grandChildNode.getTagName();
						final Object grandChildObject = decodeObjectForKey(_key);
						((EOEnterpriseObject) anObject).validateTakeValueForKeyPath(grandChildObject, _key);
						_decodingStack.pop();
					}
				}
			}
		} catch (final Exception e) {
			throw new WOXMLException(e, ":Unable to create EO.");
		}
		return anObject;
	}

	@Deprecated
	public String parserClassName() {
		return _parserClassName;
	}

	@Deprecated
	public void setParserrClassName(final String newValue) {
		_parserClassName = newValue;
	}

	public String xmlReaderClassName() {
		return _parserClassName;
	}

	public void setXMLReaderClassName(final String newValue) {
		_parserClassName = newValue;
	}

	public static WOXMLDecoder decoder() {
		return new WOXMLDecoder();
	}

	public static WOXMLDecoder decoderWithMapping(final String mappingModelFile) {
		return new _WOXMLMappingDecoder(mappingModelFile);
	}

	@Deprecated
	protected _DecodingHandler handler() {
		return new _DecodingHandler(this);
	}

	protected _DecodingContentHandler contentHandler() {
		return new _DecodingContentHandler(this);
	}

	@Deprecated
	public Parser parser() {
		if (_parser == null) {
			final Class<Parser> parserClass = _NSUtilities.classWithName(parserClassName());
			if (parserClass == null) {
				throw new WOXMLException("Could not find parser class named " + parserClassName());
			}
			try {
				_parser = parserClass.newInstance();
			} catch (final InstantiationException | IllegalAccessException e2) {
				throw new WOXMLException(e2, "Could not create parser of class:" + parserClassName());
			}
			_handler = handler();
			_parser.setDocumentHandler(_handler);
		}
		return _parser;
	}

	public XMLReader xmlReader() {
		if (_xmlReader == null) {
			try {
				final Class<XMLReader> xmlReaderClass = (Class) Class.forName(xmlReaderClassName());
				_xmlReader = xmlReaderClass.newInstance();
			} catch (final LinkageError | ClassNotFoundException exception) {
				throw new WOXMLException("Could not find parser class named " + xmlReaderClassName());
			} catch (final InstantiationException | IllegalAccessException exception) {
				throw new WOXMLException(exception, "Could not create parser of class:" + xmlReaderClassName());
			}
			_contentHandler = contentHandler();
			_xmlReader.setContentHandler(_contentHandler);
		}
		return _xmlReader;
	}

	public synchronized void setEncoding(final String encoding) {
		_encoding = encoding;
	}

	public synchronized String encoding() {
		return _encoding;
	}

	public synchronized Object decodeRootObject(final String xmlFile) {
		final InputSource is = new InputSource(xmlFile);
		return decodeRootObject(is);
	}

	public synchronized Object decodeRootObject(final NSData data) {
		Object result = null;
		if (data != null) {
			final InputStream s = new ByteArrayInputStream(data.bytes());
			final InputSource is = new InputSource(s);
			result = decodeRootObject(is);
		}
		return result;
	}

	public synchronized Object decodeRootObject(final InputSource is) {
		if (xmlReader() != null) {
			return decodeRootObjectWithXMLReader(is);
		}
		return decodeRootObjectWithParser(is);
	}

	@Deprecated
	private Object decodeRootObjectWithParser(final InputSource is) {
		try {
			if (_encoding != null) {
				is.setEncoding(_encoding);
			}
			final Parser parser = parser();
			synchronized (parser) {
				parser.parse(is);
			}
		} catch (final SAXException se) {
			throw new WOXMLException(se.getException() != null ? se.getException() : se);
		} catch (final IOException ioe) {
			throw new WOXMLException(ioe);
		}
		final Object result = _handler.root();
		_handler.reset();
		return result;
	}

	private Object decodeRootObjectWithXMLReader(final InputSource is) {
		try {
			if (_encoding != null) {
				is.setEncoding(_encoding);
			}
			final XMLReader reader = xmlReader();
			synchronized (reader) {
				reader.parse(is);
			}
		} catch (final SAXException se) {
			throw new WOXMLException(se.getException() != null ? se.getException() : se);
		} catch (final IOException ioe) {
			throw new WOXMLException(ioe);
		}
		final Object result = _contentHandler.root();
		_contentHandler.reset();
		return result;
	}

	protected String getChildNodeType(final _DecodingNode childNode) {
		return childNode.getType();
	}

	public Object decodeObjectForKey(final String aKey) {
		_DecodingNode childNode;
		Object anObject = null;
		Constructor<?> aConstructor = null;
		if (_useTopOfStack) {
			childNode = _decodingStack.peek();
		} else {
			childNode = _elementForKey(aKey);
		}
		String aType = getChildNodeType(childNode);
		if ("?".equals(aType)) {
			return null;
		}
		if ("relationship".equals(aType)) {
			final String relationshipType = childNode.getRelationshipType();
			if ("to-one".equals(relationshipType)) {
				final _DecodingNode relationshipNode = (_DecodingNode) childNode.getChildren().objectAtIndex(0);
				_decodingStack.pop();
				_decodingStack.push(relationshipNode);
				aType = relationshipNode.getType();
				childNode = relationshipNode;
			}
		}
		final Class<?> aClass = _NSUtilities.classWithName(aType);
		if (aClass == null) {
			throw new WOXMLException("Objects must be non-null and implement the WOXMLCoding interface.");
		}
		if (WOXMLCoding.class.isAssignableFrom(aClass)) {
			final String objectIDRef = childNode.getObjectIDRef();
			if (objectIDRef != null) {
				anObject = _decodedObjectCache.objectForKey(objectIDRef);
			} else {
				final String woXMLCodingID = childNode.getObjectID();
				final Class[] parameterTypes = new Class[1];
				parameterTypes[0] = WOXMLDecoder.class;
				try {
					aConstructor = aClass.getConstructor(parameterTypes);
					final Object[] constructorArgs = new Object[1];
					constructorArgs[0] = this;
					_useTopOfStack = false;
					_decodingStack.push(childNode);
					anObject = aConstructor.newInstance(constructorArgs);
					if (woXMLCodingID != null) {
						_decodedObjectCache.setObjectForKey(anObject, woXMLCodingID);
					}
					_decodingStack.pop();
				} catch (final NoSuchMethodException e) {
					throw new WOXMLException(e, ": Missing constructor " + aClass.getName()
							+ "(com.webobjects.appserver.xml.WOXMLDecoder).");
				} catch (final InvocationTargetException e) {
					throw new WOXMLException(e.getTargetException());
				} catch (final SecurityException | IllegalAccessException | InstantiationException e) {
					throw new WOXMLException(e);
				}
			}
		} else if (String.class.isAssignableFrom(aClass)) {
			anObject = _decodeString(childNode, aClass);
		} else if (NSTimestamp.class.isAssignableFrom(aClass)) {
			anObject = _decodeDate(childNode, aClass);
		} else if (_EOCheapCopyMutableArray.class.isAssignableFrom(aClass)) {
			anObject = new _EOCheapCopyMutableArray((NSArray) _decodeArray(childNode, NSMutableArray.class));
		} else if (NSArray.class.isAssignableFrom(aClass)) {
			anObject = _decodeArray(childNode, aClass);
		} else if (NSDictionary.class.isAssignableFrom(aClass)) {
			anObject = _decodeDictionary(childNode, aClass);
		} else if (NSData.class.isAssignableFrom(aClass)) {
			anObject = _decodeData(childNode, aClass);
		} else if (EOEnterpriseObject.class.isAssignableFrom(aClass)) {
			anObject = _decodeEO(childNode, aClass);
		} else if (Number.class.isAssignableFrom(aClass)) {
			anObject = _decodeNumber(childNode, aClass);
		} else if (Boolean.class.isAssignableFrom(aClass)) {
			anObject = _decodeBoolean(childNode, aClass);
		}
		return anObject;
	}

	public boolean decodeBooleanForKey(final String aKey) {
		final _DecodingNode matchingNode = _elementForKey(aKey);
		return "True".equals(matchingNode.getContent());
	}

	public int decodeIntForKey(final String aKey) {
		final _DecodingNode matchingNode = _elementForKey(aKey);
		final String intInStringForm = matchingNode.getContent();
		return Integer.parseInt(intInStringForm);
	}

	public float decodeFloatForKey(final String aKey) {
		final _DecodingNode matchingNode = _elementForKey(aKey);
		final String floatInStringForm = matchingNode.getContent();
		return Float.parseFloat(floatInStringForm);
	}

	public double decodeDoubleForKey(final String aKey) {
		final _DecodingNode matchingNode = _elementForKey(aKey);
		final String doubleInStringForm = matchingNode.getContent();
		return Double.parseDouble(doubleInStringForm);
	}

	@Override
	public Object valueForKey(final String key) {
		return NSKeyValueCoding.DefaultImplementation.valueForKey(this, key);
	}

	@Override
	public void takeValueForKey(final Object value, final String key) {
		NSKeyValueCoding.DefaultImplementation.takeValueForKey(this, value, key);
	}
}