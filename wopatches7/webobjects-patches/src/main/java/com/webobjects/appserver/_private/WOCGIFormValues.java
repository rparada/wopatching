package com.webobjects.appserver._private;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Map;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

public class WOCGIFormValues {
	protected String _woURLEncoding;

	protected static WOCGIFormValues _instance;

	public static final String WOURLEncoding = "WOURLEncoding";

	public static class Encoder {

		public String encodeAsCGIFormValues(final Map<String, Object> values, final String encoding) {
			return encodeAsCGIFormValues(values, encoding, false);
		}

		public String encodeAsCGIFormValues(final Map<String, Object> values, final String encoding,
				final boolean entityEscapeAmpersand) {
			final NSMutableArray<Object> aList = new NSMutableArray<>();
			if (values != null) {
				for (final String key : values.keySet()) {
					aList.addObjectsFromArray(encodeObject(values.get(key), encode(key, encoding), encoding));
				}
			}
			if (entityEscapeAmpersand) {
				return aList.componentsJoinedByString("&amp;");
			}
			return aList.componentsJoinedByString("&");
		}

		public NSArray<Object> encodeObject(final Object value, final String path, final String encoding) {
			NSArray<Object> aList = NSArray.emptyArray();
			if (value != null) {
				if (value instanceof NSDictionary) {
					aList = encodeDictionary((NSDictionary) value, path, encoding);
				} else if (value instanceof NSArray) {
					aList = encodeArray((NSArray) value, path, encoding);
				} else {
					aList = encodeString(value.toString(), path, encoding);
				}
			}
			return aList;
		}

		public NSArray<Object> encodeDictionary(final NSDictionary values, final String path, final String encoding) {
			final NSMutableArray<Object> aList = new NSMutableArray<>();
			if (values != null) {
				for (final Enumeration<String> enumeration = values.keyEnumerator(); enumeration.hasMoreElements();) {
					final String key = enumeration.nextElement();
					final String keyPath = (path != null && path.length() > 0 ? path + "." : "")
							+ encode(key, encoding);
					aList.addObjectsFromArray(encodeObject(values.objectForKey(key), keyPath, encoding));
				}
			}
			return aList;
		}

		public NSArray<Object> encodeArray(final NSArray values, final String path, final String encoding) {
			final NSMutableArray<Object> aList = new NSMutableArray<>();
			if (values != null) {
				for (final Object value : values) {
					aList.addObjectsFromArray(encodeObject(value, path, encoding));
				}
			}
			return aList;
		}

		public NSArray<Object> encodeString(final String value, final String path, final String encoding) {
			if (value != null) {
				return new NSArray<>(path + "=" + encode(value, encoding));
			}
			return NSArray.emptyArray();
		}

		protected String encode(final String value, final String encoding) {
			try {
				return URLEncoder.encode(value, encoding);
			} catch (final Exception exception) {
				if (NSLog._debugLoggingAllowedForLevel(NSLog.DebugLevelDetailed)) {
					NSLog.out.appendln("encode() exception " + exception);
				}
				return value;
			}
		}
	}

	public static class Decoder {
		public NSDictionary<String, NSArray<Object>> decodeCGIFormValues(final String value, final String encoding) {
			final NSMutableDictionary<String, NSMutableArray<Object>> formValues = new NSMutableDictionary<>();
			final NSArray<String> aList = NSArray.componentsSeparatedByString(value, "&");
			if (aList != null && aList.count() > 0) {
				for (final String string : aList) {
					decodeObject(formValues, string, encoding);
				}
			}
			final NSMutableDictionary<String, NSArray<Object>> result = new NSMutableDictionary<>();
			for (final String key : formValues.keySet()) {
				result.setObjectForKey(formValues.objectForKey(key).immutableClone(), key);
			}
			return result.immutableClone();
		}

		public void decodeObject(final NSMutableDictionary<String, NSMutableArray<Object>> formValues,
				final String encodedValue, final String encoding) {
			if (encodedValue.length() > 0) {
				final int indexEqual = encodedValue.indexOf("=");
				final String encodedKeyPath = getKeyPath(encodedValue, indexEqual);
				final String value = getValue(encodedValue, indexEqual);
				setObjectForKeyInDictionary(decode(value, encoding), decode(encodedKeyPath, encoding), formValues);
			}
		}

		protected void setObjectForKeyInDictionary(final Object value, final String key,
				final NSMutableDictionary<String, NSMutableArray<Object>> dictionary) {
			NSMutableArray<Object> currentValue = dictionary.objectForKey(key);
			if (currentValue == null) {
				currentValue = new NSMutableArray<>();
				dictionary.setObjectForKey(currentValue, key);
			}
			currentValue.add(value);
		}

		protected String getRootKeyPath(final String encodedKeyPath, final int indexDot) {
			if (indexDot <= 0) {
				return "";
			}
			return encodedKeyPath.substring(0, indexDot);
		}

		protected String getRemainingKeyPath(final String encodedKeyPath, final int indexDot) {
			if (indexDot == -1) {
				return encodedKeyPath;
			}
			if (indexDot + 1 < encodedKeyPath.length()) {
				return encodedKeyPath.substring(indexDot + 1);
			}
			return "";
		}

		protected String getKeyPath(final String encodedValue, final int indexEqual) {
			if (indexEqual <= 0) {
				return "WOIsmapCoords";
			}
			return encodedValue.substring(0, indexEqual);
		}

		protected String getValue(final String encodedValue, final int indexEqual) {
			if (indexEqual == -1) {
				return encodedValue;
			}
			if (indexEqual + 1 < encodedValue.length()) {
				return encodedValue.substring(indexEqual + 1);
			}
			return "";
		}

		protected String decode(final String value, final String encoding) {
			try {
				return URLDecoder.decode(value, encoding);
			} catch (final Exception exception) {
				return value;
			}
		}
	}

	public static WOCGIFormValues getInstance() {
		if (_instance == null) {
			_instance = new WOCGIFormValues();
		}
		return _instance;
	}

	public Encoder encoder() {
		return new Encoder();
	}

	public Decoder decoder() {
		return new Decoder();
	}

	public String encodeAsCGIFormValues(final Map<String, Object> values) {
		return encodeAsCGIFormValues(values, false);
	}

	public String encodeAsCGIFormValues(final Map<String, Object> values, final boolean entityEscapeAmpersand) {
		String encoding = (String) values.get("WOURLEncoding");
		if (encoding == null) {
			encoding = urlEncoding();
		}
		return encoder().encodeAsCGIFormValues(values, encoding, entityEscapeAmpersand);
	}

	public NSDictionary decodeCGIFormValues(final String value) {
		return decodeCGIFormValues(value, getWOURLEncoding(value));
	}

	public NSDictionary decodeCGIFormValues(final String value, final String encoding) {
		return decoder().decodeCGIFormValues(value, encoding);
	}

	public NSDictionary decodeDataFormValues(final String value, final String encoding) {
		return decoder().decodeCGIFormValues(value, encoding);
	}

	public String getWOURLEncoding(final String value) {
		final int i = value.indexOf("WOURLEncoding=");
		if (i >= 0) {
			final int j = i + "WOURLEncoding".length() + 1;
			final int k = value.indexOf('&', j + 1);
			if (k >= 0) {
				return value.substring(j, k);
			}
			return value.substring(j);
		}
		return urlEncoding();
	}

	public void setUrlEncoding(final String value) {
		_woURLEncoding = value;
	}

	public String urlEncoding() {
		return _woURLEncoding != null ? _woURLEncoding : "UTF-8";
	}
}