package com.webobjects.foundation.xml;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.NotActiveException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import com.webobjects.foundation.NSLog;
import com.webobjects.foundation._NSBase64;
import com.webobjects.foundation._NSObjectStreamClass;
import com.webobjects.foundation._NSSerialFieldDesc;

public class NSXMLOutputStream extends ObjectOutputStream implements NSXMLObjectStreamConstants, NSXMLObjectOutput {
	private static final class _LightweightObjectIntMap {
		private int _nextInt;

		private int _threshold;

		private float _loadFactor;

		private int[] _spine;

		private int[] _next;

		private Object[] _objs;

		private void insert(Object obj, int handle) {
			int index = hash(obj) % _spine.length;
			_objs[handle] = obj;
			_next[handle] = _spine[index];
			_spine[index] = handle;
		}

		private void growSpine() {
			_spine = new int[(_spine.length << 1) + 1];
			_threshold = (int) (_spine.length * _loadFactor);
			Arrays.fill(_spine, -1);
			for (int i = 0; i < _nextInt; i++) {
				insert(_objs[i], i);
			}
		}

		private void growEntries() {
			int newLength = (_next.length << 1) + 1;
			int[] newNext = new int[newLength];
			System.arraycopy(_next, 0, newNext, 0, _nextInt);
			_next = newNext;
			Object[] newObjs = new Object[newLength];
			System.arraycopy(_objs, 0, newObjs, 0, _nextInt);
			_objs = newObjs;
		}

		private int hash(Object obj) {
			return System.identityHashCode(obj) & Integer.MAX_VALUE;
		}

		_LightweightObjectIntMap(int initialCapacity, float loadFactor) {
			_loadFactor = loadFactor;
			_spine = new int[initialCapacity];
			_next = new int[initialCapacity];
			_objs = new Object[initialCapacity];
			_threshold = (int) (initialCapacity * _loadFactor);
			clear();
		}

		int assign(Object obj) {
			if (_nextInt >= _next.length) {
				growEntries();
			}
			if (_nextInt >= _threshold) {
				growSpine();
			}
			insert(obj, _nextInt);
			return _nextInt++;
		}

		int lookup(Object obj) {
			int index = hash(obj) % _spine.length;
			for (int i = _spine[index]; i >= 0; i = _next[i]) {
				if (_objs[i] == obj) {
					return i;
				}
			}
			return -1;
		}

		void clear() {
			Arrays.fill(_spine, -1);
			Arrays.fill(_objs, 0, _nextInt, (Object) null);
			_nextInt = 0;
		}

		int size() {
			return _nextInt;
		}
	}

	private static final class _LightweightReplaceMap {
		private NSXMLOutputStream._LightweightObjectIntMap _indices;

		private Object[] _replacements;

		private void grow() {
			Object[] newSubs = new Object[(_replacements.length << 1) + 1];
			System.arraycopy(_replacements, 0, newSubs, 0, _replacements.length);
			_replacements = newSubs;
		}

		_LightweightReplaceMap(int initialCapacity, float loadFactor) {
			_indices = new NSXMLOutputStream._LightweightObjectIntMap(initialCapacity, loadFactor);
			_replacements = new Object[initialCapacity];
		}

		void assign(Object obj, Object rep) {
			int index = _indices.assign(obj);
			while (index >= _replacements.length) {
				grow();
			}
			_replacements[index] = rep;
		}

		Object lookup(Object obj) {
			int index = _indices.lookup(obj);
			return index >= 0 ? _replacements[index] : obj;
		}

		void clear() {
			Arrays.fill(_replacements, 0, _indices.size(), (Object) null);
			_indices.clear();
		}

		int size() {
			return _indices.size();
		}
	}

	static final class DOMRecursiveInfo {
		Element _curParent;

		boolean _leafClass;

		DOMRecursiveInfo(Element parent) {
			_curParent = parent;
		}

		DOMRecursiveInfo(Element parent, boolean leaf) {
			_curParent = parent;
			_leafClass = leaf;
		}
	}

	private static final class OverriddenMtdsRecursiveInfo {
		Object _curObj;

		_NSXMLObjectStreamClass _curDesc;

		_NSXMLPutField _curPutField;

		OverriddenMtdsRecursiveInfo(Object curObj, _NSXMLObjectStreamClass curDesc, _NSXMLPutField putField) {
			_curObj = curObj;
			_curDesc = curDesc;
			_curPutField = putField;
		}

		boolean isActive() {
			return _curObj != null && _curDesc != null;
		}
	}

	private boolean _useBase64ForBinaryData = true;

	private boolean _useReferenceForString = true;

	private static final DocumentBuilderFactory _DOM_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

	static {
		_DOM_BUILDER_FACTORY.setAttribute("http://xml.org/sax/features/validation", Boolean.FALSE);
		_DOM_BUILDER_FACTORY.setNamespaceAware(true);
	}

	private static final TransformerFactory _TRX_FACTORY = TransformerFactory.newInstance();

	private static final String XMLNS_WOXML = "xmlns";

	private static final String SCHEMA_LOC = "http://www.apple.com/webobjects/5.2/schemas/woxml.xsd";

	private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

	private static final String XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/";

	private static final String ARRAY_DELIMITER = " ";

	static final String BASE64_TYPE = "base64";

	static final String BASE64_ENCODING = "US-ASCII";

	DOMImplementation _dom;

	Document _doc;

	private int _curHandle;

	private DOMRecursiveInfo _DOMRecursiveInfo;

	private OverriddenMtdsRecursiveInfo _OvMtdsRecursiveInfo;

	private NSXMLOutputFormat _outputFormat;

	private int _useReferenceForStringLength;

	private int _depth;

	private _LightweightObjectIntMap _handles;

	private _LightweightReplaceMap _replacements;

	private OutputStream _out;

	private Transformer _transformer;

	static Element createTextElement(Document targetDoc, String NodeName, String textData) {
		Element element = targetDoc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", NodeName);
		Text text = targetDoc.createTextNode(textData);
		element.appendChild(text);
		return element;
	}

	private Element writeTextElement(String NodeName, String textData) {
		Element element = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", NodeName);
		Text text = _doc.createTextNode(textData);
		element.appendChild(text);
		_DOMRecursiveInfo._curParent.appendChild(element);
		return element;
	}

	private void writeTextElement(String NodeName, String textData, String key) {
		Element ele = writeTextElement(NodeName, textData);
		if (key != null) {
			ele.setAttribute("key", key);
		}
	}

	private void clear() {
		_replacements.clear();
		_handles.clear();
		_depth = 0;
	}

	private Element writeElement(String tagName, String key) {
		Element element = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", tagName);
		if (key != null) {
			element.setAttribute("key", key);
		}
		_DOMRecursiveInfo._curParent.appendChild(element);
		return element;
	}

	private Element writeHandle(String tagName, String key) {
		Element ele = writeElement(tagName, key);
		ele.setAttribute("idRef", String.valueOf(_curHandle));
		return ele;
	}

	private Element writePrimitiveString(String str) {
		Element ele = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "string");
		char[] arr = str.toCharArray();
		StringBuilder sanitizedString = new StringBuilder(arr.length);
		for (int i = 0; i < arr.length; i++) {
			if ((arr[i] >= ' ' || arr[i] == '\n' || arr[i] == '\t') && arr[i] != '' && arr[i] != '￾'
					&& arr[i] != Character.MAX_VALUE) {
				sanitizedString.append(arr[i]);
			} else {
				String ch;
				if (sanitizedString.length() > 0) {
					ele.appendChild(_doc.createTextNode(sanitizedString.toString()));
				}
				if (arr[i] < '\020') {
					ch = "\\u000" + Integer.toHexString(arr[i]);
				} else if (arr[i] <= '') {
					ch = "\\u00" + Integer.toHexString(arr[i]);
				} else {
					ch = "\\u" + Integer.toHexString(arr[i]);
				}
				ele.appendChild(createTextElement(_doc, "ch", ch));
				sanitizedString = new StringBuilder(arr.length - i);
			}
		}
		if (sanitizedString.length() > 0) {
			ele.appendChild(_doc.createTextNode(sanitizedString.toString()));
		}
		ele.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
		return ele;
	}

	private Element writeString(String str, String key) {
		if (_useReferenceForString
				|| _useReferenceForStringLength != -1 && str.length() > _useReferenceForStringLength) {
			_curHandle = _handles.lookup(str);
			if (_curHandle != -1) {
				return writeHandle("string", key);
			}
		}
		_curHandle = _handles.assign(str);
		Element ele = writePrimitiveString(str);
		ele.setAttribute("id", String.valueOf(_curHandle));
		if (key != null) {
			ele.setAttribute("key", key);
		}
		_DOMRecursiveInfo._curParent.appendChild(ele);
		return ele;
	}

	private Element writeClassObject(Class cl, String key) {
		Element ele = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "object");
		DOMRecursiveInfo old = _DOMRecursiveInfo;
		_DOMRecursiveInfo = new DOMRecursiveInfo(ele, true);
		writeClassDesc(_NSXMLObjectStreamClass.NSXMLlookup(cl));
		ele.setAttribute("id", String.valueOf(_handles.assign(cl)));
		ele.setAttribute("type", "java.lang.Class");
		if (key != null) {
			ele.setAttribute("key", key);
		}
		_DOMRecursiveInfo = old;
		_DOMRecursiveInfo._curParent.appendChild(ele);
		return ele;
	}

	private Object invoke(Object obj, Method method, Object[] args) throws IOException {
		try {
			return method.invoke(obj, args);
		} catch (IllegalAccessException e) {
			throw new IOException(e.getMessage());
		} catch (InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if (t instanceof IOException) {
				throw (IOException) t;
			}
			throw new IOException(t.getMessage());
		}
	}

	private Object findReplacement(Object obj) throws IOException {
		if (obj instanceof String || obj.getClass().isArray()) {
			return obj;
		}
		Object newObj = obj;
		Class<?> cl = newObj.getClass();
		while (true) {
			_NSXMLObjectStreamClass desc = _NSXMLObjectStreamClass.NSXMLlookup(newObj.getClass());
			Class<?> repCl;
			if (desc == null || !desc.hasWriteReplace()
					|| (newObj = invoke(newObj, desc.writeReplaceMethod(), null)) == null
					|| (repCl = newObj.getClass()) == cl) {
				break;
			}
			cl = repCl;
		}
		return newObj;
	}

	private Element writeObjectStreamClass(ObjectStreamClass osc, String key) {
		Element ele = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "object");
		DOMRecursiveInfo old = _DOMRecursiveInfo;
		_DOMRecursiveInfo = new DOMRecursiveInfo(ele, true);
		writeClassDesc(_NSXMLObjectStreamClass.NSXMLlookup(osc.forClass()));
		ele.setAttribute("type", "java.io.ObjectStreamClass");
		if (key != null) {
			ele.setAttribute("key", key);
		}
		_DOMRecursiveInfo = old;
		_DOMRecursiveInfo._curParent.appendChild(ele);
		return ele;
	}

	Element writeInternalObject(Object obj, String key) throws IOException {
		_depth++;
		Element ele = null;
		Object newObj = obj;
		try {
			boolean replaced = false;
			while (true) {
				newObj = _replacements.lookup(newObj);
				if (newObj == null) {
					return writeElement("object", key);
				}
				if (newObj instanceof Class) {
					return writeClassObject((Class) newObj, key);
				}
				if (newObj instanceof ObjectStreamClass) {
					return writeObjectStreamClass((ObjectStreamClass) newObj, key);
				}
				Object rep;
				if (replaced || (rep = findReplacement(newObj)) == newObj) {
					break;
				}
				newObj = rep;
				replaced = true;
			}
			if (newObj instanceof String) {
				ele = writeString((String) newObj, key);
			} else if (newObj.getClass().isArray()) {
				ele = writeArray(newObj, key);
			} else if (newObj instanceof Serializable) {
				ele = writeNormalObject((Serializable) newObj, key);
			} else {
				throw new NotSerializableException(newObj.getClass().getName());
			}
		} finally {
			_depth--;
		}
		return ele;
	}

	private void writeNonProxyDesc(_NSXMLObjectStreamClass desc) {
		_curHandle = _handles.assign(desc);
		DOMRecursiveInfo old = _DOMRecursiveInfo;
		Element parent = desc.write(this, _DOMRecursiveInfo._curParent, _curHandle,
				_DOMRecursiveInfo._leafClass ? "class" : "super");
		_DOMRecursiveInfo = new DOMRecursiveInfo(parent);
		_NSXMLObjectStreamClass superDesc = (_NSXMLObjectStreamClass) desc.superDesc();
		if (superDesc != null) {
			writeClassDesc(superDesc);
		}
		_DOMRecursiveInfo = old;
	}

	private void writeProxyDesc(_NSXMLObjectStreamClass desc) {
		Element proxyEle = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "proxy");
		proxyEle.setAttribute("id", String.valueOf(_handles.assign(desc)));
		Class[] ifaces = desc.forClass().getInterfaces();
		for (final Class element : ifaces) {
			Element ifaceEle = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "interface");
			ifaceEle.setAttribute("name", element.getName());
			proxyEle.appendChild(ifaceEle);
		}
		_DOMRecursiveInfo._curParent.appendChild(proxyEle);
		_NSXMLObjectStreamClass aDesc = (_NSXMLObjectStreamClass) desc.superDesc();
		if (_handles.lookup(aDesc) == -1) {
			_handles.assign(aDesc);
			_handles.assign("java.lang.reflect.InvocationHandler");
		}
	}

	private void writeClassDesc(_NSXMLObjectStreamClass desc) {
		_curHandle = _handles.lookup(desc);
		if (_curHandle != -1) {
			if (!desc.isProxy()) {
				Element classEle = writeHandle(_DOMRecursiveInfo._leafClass ? "class" : "super", null);
				classEle.setAttribute("name", desc.getName());
			} else {
				writeHandle("proxy", null);
			}
		} else if (!desc.isProxy()) {
			writeNonProxyDesc(desc);
		} else {
			writeProxyDesc(desc);
		}
	}

	private void writeEndDataBlock() {
		Element lastChild = (Element) _DOMRecursiveInfo._curParent.getLastChild();
		String edb = lastChild.getAttribute("ignoreEDB");
		if (edb.length() == 0) {
			lastChild.setAttribute("ignoreEDB", "1");
		} else {
			try {
				int numEDB = Integer.parseInt(edb);
				lastChild.setAttribute("ignoreEDB", String.valueOf(++numEDB));
			} catch (NumberFormatException e) {
				throw new InternalError();
			}
		}
	}

	private void writeExternalizable(Externalizable obj) throws IOException {
		OverriddenMtdsRecursiveInfo old = _OvMtdsRecursiveInfo;
		_OvMtdsRecursiveInfo = new OverriddenMtdsRecursiveInfo(obj, null, null);
		obj.writeExternal(this);
		writeEndDataBlock();
		_OvMtdsRecursiveInfo = old;
	}

	private void _writeSerializable(Serializable obj, _NSXMLObjectStreamClass desc) throws IOException {
		if (desc.hasWriteObject()) {
			OverriddenMtdsRecursiveInfo old = _OvMtdsRecursiveInfo;
			_OvMtdsRecursiveInfo = new OverriddenMtdsRecursiveInfo(obj, desc, null);
			callWriteObjectOverridden();
			_OvMtdsRecursiveInfo = old;
		} else {
			writeFieldsData(obj, desc);
		}
	}

	private void writeSerializable(Serializable obj, _NSXMLObjectStreamClass desc) throws IOException {
		_NSObjectStreamClass[] hierarchy = desc.classHierarchy();
		_DOMRecursiveInfo._leafClass = false;
		for (int i = 0; i < hierarchy.length - 1; i++) {
			_NSXMLObjectStreamClass _NSXMLObjectStreamClass1 = (_NSXMLObjectStreamClass) hierarchy[i];
			_writeSerializable(obj, _NSXMLObjectStreamClass1);
		}
		_DOMRecursiveInfo._leafClass = true;
		_NSXMLObjectStreamClass localDesc = (_NSXMLObjectStreamClass) hierarchy[hierarchy.length - 1];
		_writeSerializable(obj, localDesc);
	}

	private Element createPrimFieldElement(Object obj, _NSSerialFieldDesc fieldDesc) {
		_NSXMLObjectStreamClass.PrimitiveValue pv = _NSXMLObjectStreamClass.primitiveClassToXMLTag
				.get(fieldDesc.type());
		Element ele = createTextElement(_doc, pv.tag, (String) fieldDesc.value(obj));
		_DOMRecursiveInfo._curParent.appendChild(ele);
		return ele;
	}

	private void writeProxyData(Object proxy, _NSXMLObjectStreamClass desc) throws IOException {
		_NSXMLObjectStreamClass aDesc = (_NSXMLObjectStreamClass) desc.superDesc();
		_NSSerialFieldDesc[] fieldDescs = aDesc.serializableFields();
		for (final _NSSerialFieldDesc fDesc : fieldDescs) {
			if (fDesc.isPrimitive()) {
				createPrimFieldElement(proxy, fDesc);
			} else {
				writeInternalObject(fDesc.value(proxy), null);
			}
		}
	}

	private void writeFieldsData(Object obj, _NSXMLObjectStreamClass desc) throws IOException {
		_NSSerialFieldDesc[] fieldDescs = desc.serializableFields();
		int handle = _handles.lookup(desc);
		for (final _NSSerialFieldDesc fDesc : fieldDescs) {
			Element ele = null;
			if (fDesc.isPrimitive()) {
				ele = createPrimFieldElement(obj, fDesc);
			} else {
				ele = writeInternalObject(fDesc.value(obj), null);
			}
			if (!_DOMRecursiveInfo._leafClass) {
				ele.setAttribute("classId", String.valueOf(handle));
			}
			ele.setAttribute("field", fDesc.name());
		}
	}

	private Element writeNormalObject(Serializable obj, String key) throws IOException {
		_curHandle = _handles.lookup(obj);
		if (_curHandle != -1) {
			return writeHandle("object", key);
		}
		Element objElement = writeElement("object", key);
		Class<?> cl = obj.getClass();
		_NSXMLObjectStreamClass desc = _NSXMLObjectStreamClass.NSXMLlookup(cl);
		DOMRecursiveInfo old = _DOMRecursiveInfo;
		_DOMRecursiveInfo = new DOMRecursiveInfo(objElement, true);
		writeClassDesc(desc);
		objElement.setAttribute("id", String.valueOf(_handles.assign(obj)));
		if (!desc.isProxy()) {
			if (!Externalizable.class.isAssignableFrom(cl)) {
				writeSerializable(obj, desc);
			} else {
				writeExternalizable((Externalizable) obj);
			}
		} else {
			writeProxyData(obj, desc);
		}
		_DOMRecursiveInfo = old;
		return objElement;
	}

	private int writeArrayData(Object array, Element parent, _NSXMLObjectStreamClass desc) throws IOException {
		Class<?> ctype = desc.forClass().getComponentType();
		int len = 0;
		if (ctype.isPrimitive()) {
			StringBuilder buf = new StringBuilder(len * 2);
			if (ctype == boolean.class) {
				boolean[] za = (boolean[]) array;
				len = za.length;
				for (int i = 0; i < len; i++) {
					buf.append(za[i]);
					buf.append(" ");
				}
			} else if (ctype == byte.class) {
				byte[] ba = (byte[]) array;
				len = ba.length;
				if (_useBase64ForBinaryData) {
					parent.appendChild(_doc.createTextNode(new String(_NSBase64.encode(ba, 0, len), "US-ASCII")));
					return len;
				}
				for (int i = 0; i < len; i++) {
					buf.append(ba[i]);
					buf.append(" ");
				}
			} else if (ctype == short.class) {
				short[] sa = (short[]) array;
				len = sa.length;
				for (int i = 0; i < len; i++) {
					buf.append(sa[i]);
					buf.append(" ");
				}
			} else if (ctype == char.class) {
				char[] ca = (char[]) array;
				len = ca.length;
				for (int i = 0; i < len; i++) {
					buf.append(_NSSerialFieldDesc.sanitizeCharForXML(ca[i], true));
					buf.append(" ");
				}
			} else if (ctype == int.class) {
				int[] ia = (int[]) array;
				len = ia.length;
				for (int i = 0; i < len; i++) {
					buf.append(ia[i]);
					buf.append(" ");
				}
			} else if (ctype == long.class) {
				long[] ja = (long[]) array;
				len = ja.length;
				for (int i = 0; i < len; i++) {
					buf.append(ja[i]);
					buf.append(" ");
				}
			} else if (ctype == float.class) {
				float[] fa = (float[]) array;
				len = fa.length;
				for (int i = 0; i < len; i++) {
					buf.append(fa[i]);
					buf.append(" ");
				}
			} else if (ctype == double.class) {
				double[] da = (double[]) array;
				len = da.length;
				for (int i = 0; i < len; i++) {
					buf.append(da[i]);
					buf.append(" ");
				}
			}
			parent.appendChild(_doc.createTextNode(buf.toString()));
		} else {
			Object[] objs = (Object[]) array;
			len = objs.length;
			for (int i = 0; i < len; i++) {
				parent.appendChild(writeInternalObject(objs[i], null));
			}
		}
		return len;
	}

	private Element writeArray(Object array, String key) throws IOException {
		_curHandle = _handles.lookup(array);
		if (_curHandle != -1) {
			return writeHandle("array", key);
		}
		Element ele = writeElement("array", key);
		_NSXMLObjectStreamClass desc = _NSXMLObjectStreamClass.NSXMLlookup(array.getClass());
		desc.writeArray(this, ele, _handles.assign(desc));
		ele.setAttribute("id", String.valueOf(_handles.assign(array)));
		ele.setAttribute("length", String.valueOf(writeArrayData(array, ele, desc)));
		return ele;
	}

	private void writeFinalException(IOException e) throws StreamCorruptedException {
		clear();
		Element ele = _doc.createElementNS("http://www.apple.com/webobjects/XMLSerialization", "finalException");
		_NSXMLObjectStreamClass desc = _NSXMLObjectStreamClass.NSXMLlookup(e.getClass());
		Element root = _doc.getDocumentElement();
		_DOMRecursiveInfo = new DOMRecursiveInfo(ele, true);
		writeClassDesc(desc);
		ele.setAttribute("id", String.valueOf(_handles.assign(e)));
		try {
			writeSerializable(e, desc);
		} catch (IOException ex) {
			throw new StreamCorruptedException(ex.getMessage());
		}
		root.appendChild(ele);
		clear();
	}

	private void callWriteObjectOverridden() throws IOException {
		invoke(_OvMtdsRecursiveInfo._curObj, _OvMtdsRecursiveInfo._curDesc.writeObjectMethod(), new Object[] { this });
		writeEndDataBlock();
	}

	private boolean isOverridden() {
		return _OvMtdsRecursiveInfo != null && _OvMtdsRecursiveInfo.isActive();
	}

	private void initWithTransformer(Source source) throws IOException {
		try {
			_transformer = _TRX_FACTORY.newTransformer(source);
		} catch (TransformerConfigurationException e) {
			throw new IOException(e.getMessage());
		}
	}

	private boolean setUseReferenceForString(boolean on) {
		boolean old = _useReferenceForString;
		_useReferenceForString = on;
		_useReferenceForStringLength = -1;
		return old;
	}

	void setFieldType(Element element, String typeName) {
		_handles.assign(typeName);
		element.setAttribute("type", typeName);
	}

	Element curParent() {
		return _DOMRecursiveInfo._curParent;
	}

	@Override
	protected void writeStreamHeader() throws IOException {
		Element rootEle = _doc.getDocumentElement();
		_DOMRecursiveInfo = new DOMRecursiveInfo(rootEle, true);
		rootEle.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns",
				"http://www.apple.com/webobjects/XMLSerialization");
		rootEle.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi",
				"http://www.w3.org/2001/XMLSchema-instance");
		rootEle.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
				"http://www.apple.com/webobjects/XMLSerialization\nhttp://www.apple.com/webobjects/5.2/schemas/woxml.xsd");
	}

	@Override
	protected void writeObjectOverride(Object obj) throws IOException {
		try {
			writeInternalObject(obj, null);
		} catch (IOException ex) {
			if (_depth == 0) {
				writeFinalException(ex);
			}
			throw ex;
		}
	}

	public NSXMLOutputStream(OutputStream out, File xslt) throws IOException {
		this(out);
		initWithTransformer(new StreamSource(xslt));
	}

	public NSXMLOutputStream(OutputStream out, InputSource xslt) throws IOException {
		this(out);
		initWithTransformer(new SAXSource(xslt));
	}

	public NSXMLOutputStream(OutputStream out, Transformer transformer) throws IOException {
		this(out);
		_transformer = transformer;
	}

	public NSXMLOutputStream(OutputStream out) throws IOException {
		_out = out;
		_handles = new _LightweightObjectIntMap(20, 3.0F);
		_replacements = new _LightweightReplaceMap(10, 3.0F);
		_outputFormat = new NSXMLOutputFormat();
		_outputFormat.setIndenting(false);
		try {
			_dom = _DOM_BUILDER_FACTORY.newDocumentBuilder().getDOMImplementation();
		} catch (ParserConfigurationException e) {
			throw new IOException(e.getMessage());
		}
		_doc = _dom.createDocument("http://www.apple.com/webobjects/XMLSerialization", "content", null);
		writeStreamHeader();
	}

	public void setXSLTSource(File xslt) throws IOException {
		initWithTransformer(new StreamSource(xslt));
	}

	public void setXSLTSource(InputSource xslt) throws IOException {
		initWithTransformer(new SAXSource(xslt));
	}

	public void setTransformer(Transformer transformer) {
		_transformer = transformer;
	}

	public Transformer transformer() {
		return _transformer;
	}

	public NSXMLOutputFormat outputFormat() {
		return _outputFormat;
	}

	public void setOutputFormat(NSXMLOutputFormat format) {
		_outputFormat = format;
	}

	@Override
	public void defaultWriteObject() throws IOException {
		if (!isOverridden()) {
			throw new NotActiveException("This method has to be called from writeObject");
		}
		writeFieldsData(_OvMtdsRecursiveInfo._curObj, _OvMtdsRecursiveInfo._curDesc);
	}

	@Override
	public ObjectOutputStream.PutField putFields() throws IOException {
		if (!isOverridden()) {
			throw new NotActiveException("This method has to be called from writeObject");
		}
		if (_OvMtdsRecursiveInfo._curPutField == null) {
			_OvMtdsRecursiveInfo._curPutField = new _NSXMLPutField(this, _OvMtdsRecursiveInfo._curDesc,
					_DOMRecursiveInfo);
		}
		return _OvMtdsRecursiveInfo._curPutField;
	}

	@Override
	public void writeFields() throws IOException {
		if (_OvMtdsRecursiveInfo == null || _OvMtdsRecursiveInfo._curPutField == null) {
			throw new NotActiveException("Use putFields() method to get a PutField object first");
		}
		_OvMtdsRecursiveInfo._curPutField.writeFields();
	}

	@Override
	public void write(int val) throws IOException {
		writeByte(val);
	}

	@Override
	public void write(byte[] buf) throws IOException {
		write(buf, 0, buf.length);
	}

	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		write(buf, off, len, null);
	}

	@Override
	public void writeBoolean(boolean val) throws IOException {
		writeTextElement("boolean", val ? "true" : "false");
	}

	@Override
	public void writeByte(int val) throws IOException {
		writeTextElement("byte", String.valueOf((byte) val));
	}

	@Override
	public void writeShort(int val) throws IOException {
		writeTextElement("short", String.valueOf((short) val));
	}

	@Override
	public void writeChar(int val) throws IOException {
		writeTextElement("ch", _NSSerialFieldDesc.sanitizeCharForXML((char) val, false));
	}

	@Override
	public void writeInt(int val) throws IOException {
		writeTextElement("int", String.valueOf(val));
	}

	@Override
	public void writeLong(long val) throws IOException {
		writeTextElement("long", String.valueOf(val));
	}

	@Override
	public void writeFloat(float val) throws IOException {
		writeTextElement("float", String.valueOf(val));
	}

	@Override
	public void writeDouble(double val) throws IOException {
		writeTextElement("double", String.valueOf(val));
	}

	@Override
	public void writeBytes(String str) throws IOException {
		write(str.getBytes(_outputFormat.encoding()));
	}

	@Override
	public void writeChars(String str) throws IOException {
		writeChars(str, null);
	}

	@Override
	public void writeUTF(String str) throws IOException {
		_DOMRecursiveInfo._curParent.appendChild(writePrimitiveString(str));
	}

	@Override
	public void reset() throws IOException {
		if (_depth != 0) {
			throw new IOException("Currently in process of serializing an object");
		}
		clear();
		_doc = _dom.createDocument("http://www.apple.com/webobjects/XMLSerialization", "content", null);
		writeStreamHeader();
	}

	@Override
	public void flush() throws IOException {
		_out.flush();
	}

	@Override
	public void close() throws IOException {
		if (_doc != null) {
			try {
				if (_transformer == null) {
					_transformer = TransformerFactory.newInstance().newTransformer();
					_transformer.setOutputProperty(OutputKeys.INDENT, _outputFormat.indenting() ? "yes" : "no");
					_transformer.setOutputProperty(OutputKeys.ENCODING, _outputFormat.encoding());
					_transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
							_outputFormat.omitXMLDeclaration() ? "yes" : "no");
					_transformer.setOutputProperty(OutputKeys.VERSION, _outputFormat.version());
				}
				_transformer.transform(new DOMSource(_doc), new StreamResult(_out));
			} catch (TransformerException e) {
				NSLog._conditionallyLogPrivateException(e);
				throw new IOException(e.getMessage());
			}
		}
		clear();
		_out.close();
		_doc = null;
	}

	@Override
	public void write(int val, String key) {
		writeByte(val, key);
	}

	@Override
	public void write(byte[] buf, String key) throws IOException {
		write(buf, 0, buf.length, key);
	}

	@Override
	public void write(byte[] buf, int off, int len, String key) throws IOException {
		int endoff = off + len;
		if (off < 0 || len < 0 || endoff > buf.length || endoff < 0) {
			throw new IndexOutOfBoundsException();
		}
		Element ele = writeElement("array", null);
		if (_useBase64ForBinaryData) {
			ele.setAttribute("type", "base64");
			ele.appendChild(_doc.createTextNode(new String(_NSBase64.encode(buf, off, len), "US-ASCII")));
		} else {
			ele.setAttribute("type", "byte[]");
			StringBuilder strBuf = new StringBuilder(len * 2);
			for (int i = off; i < len; i++) {
				strBuf.append(buf[i]);
				strBuf.append(" ");
			}
			ele.appendChild(_doc.createTextNode(strBuf.toString()));
		}
		ele.setAttribute("length", String.valueOf(buf.length));
		if (key != null) {
			ele.setAttribute("key", key);
		}
	}

	@Override
	public void writeBoolean(boolean val, String key) {
		writeTextElement("boolean", val ? "true" : "false", key);
	}

	@Override
	public void writeByte(int val, String key) {
		writeTextElement("byte", String.valueOf(val), key);
	}

	@Override
	public void writeShort(int val, String key) {
		writeTextElement("short", String.valueOf((short) val), key);
	}

	@Override
	public void writeChar(int val, String key) {
		writeTextElement("ch", _NSSerialFieldDesc.sanitizeCharForXML((char) val, false), key);
	}

	@Override
	public void writeInt(int val, String key) {
		writeTextElement("int", String.valueOf(val), key);
	}

	@Override
	public void writeLong(long val, String key) {
		writeTextElement("long", String.valueOf(val), key);
	}

	@Override
	public void writeFloat(float val, String key) {
		writeTextElement("float", String.valueOf(val), key);
	}

	@Override
	public void writeDouble(double val, String key) {
		writeTextElement("double", String.valueOf(val), key);
	}

	@Override
	public void writeObject(Object obj, String key) throws IOException {
		writeInternalObject(obj, key);
	}

	@Override
	public void writeBytes(String str, String key) throws IOException {
		write(str.getBytes(_outputFormat.encoding()), key);
	}

	@Override
	public void writeChars(String str, String key) throws IOException {
		char[] chars = str.toCharArray();
		Element ele = writeElement("array", null);
		_NSXMLObjectStreamClass desc = _NSXMLObjectStreamClass.NSXMLlookup(chars.getClass());
		desc.writeArray(this, ele, 0);
		ele.setAttribute("length", String.valueOf(writeArrayData(chars, ele, desc)));
		if (key != null) {
			ele.setAttribute("key", key);
		}
	}

	@Override
	public void writeUTF(String str, String key) {
		Element ele = writePrimitiveString(str);
		if (key != null) {
			ele.setAttribute("key", key);
		}
		_DOMRecursiveInfo._curParent.appendChild(ele);
	}

	@Override
	public void writeComment(String comment) {
		_DOMRecursiveInfo._curParent.appendChild(_doc.createComment(comment));
	}

	@Override
	public void writeRootComment(String comment, boolean before) {
		if (before) {
			_doc.insertBefore(_doc.createComment(comment), _doc.getDocumentElement());
		} else {
			_doc.appendChild(_doc.createComment(comment));
		}
	}

	@Override
	public final boolean setUseBase64ForBinaryData(boolean on) {
		boolean old = _useBase64ForBinaryData;
		_useBase64ForBinaryData = on;
		return old;
	}

	@Override
	public final boolean useBase64ForBinaryData() {
		return _useBase64ForBinaryData;
	}

	@Override
	public boolean enableReferenceForString() {
		return setUseReferenceForString(true);
	}

	@Override
	public boolean disableReferenceForString() {
		return setUseReferenceForString(false);
	}

	@Override
	public int disableReferenceForString(int length) {
		int old = _useReferenceForStringLength;
		_useReferenceForString = false;
		_useReferenceForStringLength = length;
		return old;
	}

	@Override
	public boolean useReferenceForString() {
		return _useReferenceForString;
	}
}