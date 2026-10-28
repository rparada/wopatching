package com.webobjects.foundation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

/**
 * <div class="en"> NSArray re-implementation to support JDK 1.5 templates. Use
 * with </div>
 *
 * <div class="ja"> JDK 1.5 テンプレートをサポートする為の再実装。使用は </div>
 *
 * <pre>
 * <code>
 * NSArray&lt;Bug&gt; bugs = ds.fetchObjects();
 *
 * for(Bug : bugs) {
 * 	  ...
 * }</code>
 * </pre>
 *
 * @param <E> - type of array contents
 */
public class NSArray<E> implements Cloneable, Serializable, NSCoding, NSKeyValueCoding, NSKeyValueCodingAdditions,
		_NSFoundationCollection, List<E> {

	static final long serialVersionUID = -3789592578296478260L;

	public static class _AvgNumberOperator extends _Operator implements Operator {

		@Override
		public Object compute(final NSArray<?> values, final String keyPath) {
			final int count = values.count();
			if (count != 0) {
				final BigDecimal sum = _sum(values, keyPath);
				return sum.divide(BigDecimal.valueOf(count), sum.scale() + 4, 6);
			}
			return null;
		}
	}

	public static class _SumNumberOperator extends _Operator implements Operator {

		@Override
		public Object compute(final NSArray<?> values, final String keyPath) {
			return _sum(values, keyPath);
		}
	}

	public static class _MinOperator extends _Operator implements Operator {

		@Override
		public Object compute(final NSArray<?> values, final String keyPath) {
			Object min = null;
			final Object[] objects = values.objectsNoCopy();
			for (final Object object : objects) {
				min = _minOrMaxValue(min, _operationValue(object, keyPath), false);
			}

			return min;
		}
	}

	public static class _MaxOperator extends _Operator implements Operator {

		@Override
		public Object compute(final NSArray<?> values, final String keyPath) {
			Object max = null;
			final Object[] objects = values.objectsNoCopy();
			for (final Object object : objects) {
				max = _minOrMaxValue(max, _operationValue(object, keyPath), true);
			}

			return max;
		}
	}

	public static class _Operator {

		protected Object _operationValue(final Object object, final String keyPath) {
			return keyPath == null || keyPath.length() <= 0 ? object
					: NSKeyValueCodingAdditions.Utility.valueForKeyPath(object, keyPath);
		}

		private BigDecimal _bigDecimalForValue(final Object object) {
			if (object != null) {
				if (_NSUtilities._isClassANumberOrABoolean(object.getClass())) {
					return (BigDecimal) _NSUtilities.convertNumberOrBooleanIntoCompatibleValue(object,
							_NSUtilities._BigDecimalClass);
				}
				if (object instanceof String) {
					return new BigDecimal((String) object);
				}
				throw new IllegalStateException(
						"Can't convert " + object + " (class " + object.getClass().getName() + ") into number");
			}
			return null;
		}

		BigDecimal _sum(final NSArray<?> values, final String keyPath) {
			BigDecimal sum = BigDecimal.valueOf(0L);
			final Object[] objects = values.objectsNoCopy();
			for (final Object object : objects) {
				final BigDecimal value = _bigDecimalForValue(_operationValue(object, keyPath));
				if (value != null) {
					sum = sum.add(value);
				}
			}

			return sum;
		}

		Object _minOrMaxValue(final Object referenceValue, final Object compareValue,
				final boolean trueForMaxAndFalseForMin) {
			if (referenceValue == null) {
				return compareValue;
			}
			if (compareValue == null) {
				return referenceValue;
			}
			int comparison;
			if (_NSUtilities._isClassANumberOrABoolean(referenceValue.getClass())) {
				comparison = _NSUtilities.compareNumbersOrBooleans(referenceValue, compareValue);
			} else if (referenceValue instanceof NSTimestamp) {
				comparison = ((NSTimestamp) referenceValue).compare((NSTimestamp) compareValue);
			} else if (referenceValue instanceof Comparable) {
				comparison = ((Comparable<Object>) referenceValue).compareTo(compareValue);
			} else {
				throw new IllegalStateException("Cannot compare values " + referenceValue + " and " + compareValue
						+ " (they are not instance of Comparable");
			}
			if (trueForMaxAndFalseForMin) {
				if (comparison >= 0) {
					return referenceValue;
				}
			} else if (comparison <= 0) {
				return referenceValue;
			}
			return compareValue;
		}
	}

	public static class _CountOperator implements Operator {

		@Override
		public Object compute(final NSArray<?> values, final String keyPath) {
			return _NSUtilities.IntegerForInt(values.count());
		}
	}

	public interface Operator {

		Object compute(NSArray<?> nsarray, String s);
	}

	public static final Class _CLASS = _NSUtilitiesExtra
			._classWithFullySpecifiedNamePrime("com.webobjects.foundation.NSArray");

	public static final int NotFound = -1;
	public static final NSArray EmptyArray = new NSArray<>();
	private static final char _OperatorIndicatorChar = '@';
	public static final String CountOperatorName = "count";
	public static final String MaximumOperatorName = "max";
	public static final String MinimumOperatorName = "min";
	public static final String SumOperatorName = "sum";
	public static final String AverageOperatorName = "avg";
	private static final String SerializationValuesFieldKey = "objects";
	private static NSMutableDictionary<String, Operator> _operators = new NSMutableDictionary<>(8);
	protected static final int _NSArrayClassHashCode = _CLASS.hashCode();
	protected Object[] _objects;
	protected transient int _hashCache;
	private transient boolean _recomputeHashCode = true;
	public static final boolean CheckForNull = true;
	public static final boolean IgnoreNull = true;
	private static final ObjectStreamField[] serialPersistentFields = {
			new ObjectStreamField(SerializationValuesFieldKey, new Object[0].getClass()) };

	public static NSArray<String> operatorNames() {
		NSArray<String> operatorNames;
		synchronized (_operators) {
			operatorNames = _operators.allKeys();
		}
		return operatorNames;
	}

	public static void setOperatorForKey(final String operatorName, final Operator arrayOperator) {
		if (operatorName == null) {
			throw new IllegalArgumentException("Operator key cannot be null");
		}
		if (arrayOperator == null) {
			throw new IllegalArgumentException("Operator cannot be null for " + operatorName);
		}
		synchronized (_operators) {
			_operators.setObjectForKey(arrayOperator, operatorName);
		}
	}

	public static Operator operatorForKey(final String operatorName) {
		Operator arrayOperator;
		synchronized (_operators) {
			arrayOperator = _operators.objectForKey(operatorName);
		}
		return arrayOperator;
	}

	public static void removeOperatorForKey(final String operatorName) {
		if (operatorName != null) {
			synchronized (_operators) {
				_operators.removeObjectForKey(operatorName);
			}
		}
	}

	protected void _initializeWithCapacity(final int capacity) {
		_setObjects(capacity <= 0 ? null : new Object[capacity]);
		_setCount(0);
		_setMustRecomputeHash(true);
	}

	public NSArray() {
		this(null, 0, 0, false, false);
	}

	public NSArray(final E object) {
		if (object == null) {
			throw new IllegalArgumentException("Attempt to insert null into an NSArray");
		}
		_initializeWithCapacity(1);
		_objects()[0] = object;
		_setCount(1);

	}

	private void initFromObjects(final Object[] objects, final int rangeLocation, final int rangeLength,
			final boolean checkForNull,
			final boolean ignoreNull) {
		initFromObjects(objects, rangeLocation, rangeLength, 0, checkForNull, ignoreNull);
	}

	private void initFromObjects(final Object[] objects, final int rangeLocation, final int rangeLength,
			final int offset, final boolean checkForNull,
			final boolean ignoreNull) {
		if (checkForNull) {
			final int maxRange = rangeLocation + rangeLength;
			int validCount = 0;
			final Object[] validObjects = new Object[maxRange];
			for (int i = rangeLocation; i < maxRange; i++) {
				final Object o = objects[i];
				if (o != null) {
					validObjects[validCount++] = o;
					continue;
				}
				if (!ignoreNull) {
					throw new IllegalArgumentException("Attempt to insert null into an " + getClass().getName() + ".");
				}
			}
			_initializeWithCapacity(validCount + offset);

			if (validCount > 0) {
				System.arraycopy(validObjects, 0, _objects(), offset, validCount);
			}
			_setCount(validCount + offset);
		} else {
			_initializeWithCapacity(rangeLength + offset);
			if (rangeLength > 0) {
				System.arraycopy(objects, rangeLocation, _objects(), offset, rangeLength);
			}
			_setCount(rangeLength + offset);
		}
	}

	private void initFromList(final List<? extends E> list, final int rangeLocation, final int rangeLength,
			final int offset,
			final boolean checkForNull, final boolean ignoreNull) {
		final int maxRange = rangeLocation + rangeLength;
		if (checkForNull) {
			int validCount = 0;
			final Object[] validObjects = new Object[maxRange];
			for (int i = rangeLocation; i < maxRange; i++) {
				final Object o = list.get(i);
				if (o != null) {
					validObjects[validCount++] = o;
					continue;
				}
				if (!ignoreNull) {
					throw new IllegalArgumentException("Attempt to insert null into an " + getClass().getName() + ".");
				}
			}

			_initializeWithCapacity(validCount + offset);

			if (validCount > 0) {
				System.arraycopy(validObjects, 0, _objects(), offset, validCount);
			}
			_setCount(validCount + offset);
		} else {
			_initializeWithCapacity(rangeLength + offset);
			System.arraycopy(list.toArray(), rangeLocation, _objects(), offset, rangeLength);
			_setCount(rangeLength + offset);
		}
	}

	protected NSArray(final Object[] objects, final int rangeLocation, final int rangeLength,
			final boolean checkForNull, final boolean ignoreNull) {
		initFromObjects(objects, rangeLocation, rangeLength, checkForNull, ignoreNull);
	}

	public NSArray(final E[] objects) {
		this(objects, 0, objects == null ? 0 : objects.length, true, true);
	}

	public NSArray(final E object, final E... objects) {
		if (object == null) {
			initFromObjects(objects, 0, objects == null ? 0 : objects.length, 0, true, true);
		} else {
			initFromObjects(objects, 0, objects == null ? 0 : objects.length, 1, true, true);
			_objects()[0] = object;
		}
	}

	public NSArray(final E[] objects, final NSRange range) {
		this(objects, range == null ? 0 : range.location(), range == null ? 0 : range.length(), true, true);
	}

	public NSArray(final NSArray<? extends E> otherArray) {
		this(otherArray == null ? null : (E[]) otherArray.objectsNoCopy(), 0,
				otherArray == null ? 0 : otherArray.count(), false, false);
	}

	public NSArray(final List<? extends E> list, final boolean checkForNull) {
		if (list == null) {
			initFromObjects(null, 0, 0, false, false);
		} else {
			initFromList(list, 0, list.size(), 0, checkForNull, false);
		}
	}

	public NSArray(final Collection<? extends E> collection, final boolean checkForNull) {
		initFromObjects(collection == null ? null : collection.toArray(), 0, collection == null ? 0 : collection.size(),
				checkForNull, false);
	}

	public NSArray(final Collection<? extends E> collection) {
		this(collection, true);
	}

	public NSArray(final List<? extends E> list, final NSRange range, final boolean ignoreNull) {
		if (list == null) {
			throw new IllegalArgumentException("List cannot be null");
		}
		initFromList(list, range != null ? range.location() : 0, range != null ? range.length() : 0, 0, true,
				ignoreNull);
	}

	public NSArray(final Vector<? extends E> vector, final NSRange range, final boolean ignoreNull) {
		this((List<E>) vector, range, ignoreNull);
	}

	protected void _setCount(final int count) {
//    	if(count != count() && count != 0) {
//    		throw new IllegalStateException();
//    	}
	}

	protected Object[] _objects() {
		return _objects;
	}

	protected void _setObjects(final Object[] objects) {
		_objects = objects;
	}

	protected Object[] objectsNoCopy() {
		final Object[] objs = _objects();
		return objs != null ? objs : _NSCollectionPrimitives.EmptyArray;
	}

	public int count() {
		return _objects != null ? _objects.length : 0;
	}

	public E objectAtIndex(final int index) {
		final int count = count();
		if (index >= 0 && index < count) {
			return (E) _objects()[index];
		}
		if (count == 0) {
			throw new IllegalArgumentException("Array is empty");
		}
		throw new IllegalArgumentException("Index (" + index + ") out of bounds [0, " + (count() - 1) + "]");
	}

	public NSArray<E> arrayByAddingObject(final E object) {
		if (object == null) {
			throw new IllegalArgumentException("Attempt to insert null into an  " + getClass().getName() + ".");
		}
		final int count = count();
		final Object[] objects = new Object[count + 1];
		System.arraycopy(objectsNoCopy(), 0, objects, 0, count);
		objects[count] = object;
		return new NSArray<>(objects, 0, count + 1, false, false);
	}

	public NSArray<E> arrayByAddingObjectsFromArray(final NSArray<? extends E> otherArray) {
		if (otherArray != null) {
			final int count = count();
			final int otherCount = otherArray.count();
			if (count == 0) {
				return (NSArray<E>) otherArray.immutableClone();
			}
			if (otherCount == 0) {
				return immutableClone();
			}
			final Object[] objects = new Object[count + otherCount];
			System.arraycopy(objectsNoCopy(), 0, objects, 0, count);
			System.arraycopy(otherArray.objectsNoCopy(), 0, objects, count, otherCount);
			return new NSArray<>(objects, 0, count + otherCount, false, false);
		}
		return immutableClone();
	}

	public Object[] objects() {
		final int count = count();
		final Object[] objects = new Object[count];
		if (count > 0) {
			System.arraycopy(objectsNoCopy(), 0, objects, 0, count);
		}
		return objects;
	}

	public Object[] objects(final NSRange range) {
		if (range == null) {
			return _NSCollectionPrimitives.EmptyArray;
		}
		final int rangeLength = range.length();
		final Object[] objects = new Object[rangeLength];
		System.arraycopy(objectsNoCopy(), range.location(), objects, 0, rangeLength);
		return objects;
	}

	public Vector<E> vector() {
		final Vector<E> vector = new Vector<>();
		final E[] objects = (E[]) objectsNoCopy();
		for (final E object : objects) {
			vector.addElement(object);
		}

		return vector;
	}

	public ArrayList<E> arrayList() {
		final E[] objects = (E[]) objectsNoCopy();
		final ArrayList<E> list = new ArrayList<>(objects.length);
		Collections.addAll(list, objects);

		return list;
	}

	public boolean containsObject(final Object object) {
		if (object == null) {
			return false;
		}
		return _findObjectInArray(0, count(), object, false) != NotFound;
	}

	public E firstObjectCommonWithArray(final NSArray<?> otherArray) {
		if (otherArray == null) {
			return null;
		}
		final int otherCount = otherArray.count();
		if (otherCount > 0) {
			final Object[] objects = objectsNoCopy();
			for (final Object object : objects) {
				if (otherArray.containsObject(object)) {
					return (E) object;
				}
			}

		}
		return null;
	}

	/**
	 * @deprecated use {@link #objects()} or {@link #objectsNoCopy()}
	 */
	@Deprecated
	public void getObjects(final Object[] objects) {
		if (objects == null) {
			throw new IllegalArgumentException("Object buffer cannot be null");
		}
		System.arraycopy(objectsNoCopy(), 0, objects, 0, count());
	}

	/**
	 * @deprecated use {@link #objects(NSRange)}
	 */
	@Deprecated
	public void getObjects(final Object[] objects, final NSRange range) {
		if (objects == null) {
			throw new IllegalArgumentException("Object buffer cannot be null");
		}
		if (range == null) {
			throw new IllegalArgumentException("Range cannot be null");
		}
		System.arraycopy(objectsNoCopy(), range.location(), objects, 0, range.length());
	}

	private final int _findObjectInArray(final int index, final int length, final Object object,
			final boolean identical) {
		if (count() > 0) {
			final Object[] objects = objectsNoCopy();
			final int maxIndex = index + length - 1;
			for (int i = index; i <= maxIndex; i++) {
				if (objects[i] == object || !identical && object.equals(objects[i])) {
					return i;
				}

			}

		}
		return NotFound;
	}

	public int indexOfObject(final Object object) {
		if (object == null) {
			return NotFound;
		}
		return _findObjectInArray(0, count(), object, false);
	}

	public int indexOfObject(final Object object, final NSRange range) {
		if (object == null || range == null) {
			return NotFound;
		}
		final int count = count();
		final int rangeLocation = range.location();
		final int rangeLength = range.length();
		if (rangeLocation + rangeLength > count || rangeLocation >= count) {
			throw new IllegalArgumentException(
					"Range [" + rangeLocation + "; " + rangeLength + "] out of bounds [0, " + (count() - 1) + "]");
		}
		return _findObjectInArray(rangeLocation, rangeLength, object, false);
	}

	public int indexOfIdenticalObject(final Object object) {
		if (object == null) {
			return NotFound;
		}
		return _findObjectInArray(0, count(), object, true);
	}

	public int indexOfIdenticalObject(final Object object, final NSRange range) {
		if (object == null || range == null) {
			return NotFound;
		}
		final int count = count();
		final int rangeLocation = range.location();
		final int rangeLength = range.length();
		if (rangeLocation + rangeLength > count || rangeLocation >= count) {
			throw new IllegalArgumentException(
					"Range [" + rangeLocation + "; " + rangeLength + "] out of bounds [0, " + (count() - 1) + "]");
		}
		return _findObjectInArray(rangeLocation, rangeLength, object, true);
	}

	public NSArray<E> subarrayWithRange(final NSRange range) {
		if (range == null) {
			return EmptyArray;
		}
		return new NSArray<>(objectsNoCopy(), range.location(), range.length(), false, false);
	}

	public E lastObject() {
		final int count = count();
		return count != 0 ? objectAtIndex(count - 1) : null;
	}

	private boolean _equalsArray(final NSArray<?> otherArray) {
		final int count = count();
		if (count != otherArray.count()
				|| !_mustRecomputeHash() && !otherArray._mustRecomputeHash() && hashCode() != otherArray.hashCode()) {
			return false;
		}
		final Object[] objects = objectsNoCopy();
		final Object[] otherObjects = otherArray.objectsNoCopy();
		for (int i = 0; i < count; i++) {
			if (!objects[i].equals(otherObjects[i])) {
				return false;
			}
		}

		return true;
	}

	public boolean isEqualToArray(final NSArray<?> otherArray) {
		if (otherArray == this) {
			return true;
		}
		if (otherArray == null) {
			return false;
		}
		return _equalsArray(otherArray);
	}

	@Override
	public boolean equals(final Object object) {
		if (object == this) {
			return true;
		}
		if (object instanceof NSArray) {
			return _equalsArray((NSArray<?>) object);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public Enumeration<E> objectEnumerator() {
		return new _NSJavaArrayEnumerator(objectsNoCopy(), count(), false);
	}

	@SuppressWarnings("unchecked")
	public Enumeration<E> reverseObjectEnumerator() {
		return new _NSJavaArrayEnumerator(objectsNoCopy(), count(), true);
	}

	/**
	 * @deprecated Method sortedArrayUsingSelector is deprecated
	 */
	@Deprecated
	@SuppressWarnings("unchecked")
	public NSArray sortedArrayUsingSelector(final NSSelector selector) throws NSComparator.ComparisonException {
		final NSMutableArray array = new NSMutableArray(this);
		final NSComparator comparator = new NSComparator._NSSelectorComparator(selector);
		array.sortUsingComparator(comparator);
		return array;
	}

	public NSArray<E> sortedArrayUsingComparator(final NSComparator comparator)
			throws NSComparator.ComparisonException {
		final NSMutableArray<E> array = new NSMutableArray<>(this);
		array.sortUsingComparator(comparator);
		return array;
	}

	public String componentsJoinedByString(final String separator) {
		final Object[] objects = objectsNoCopy();
		final StringBuilder buffer = new StringBuilder(objects.length * 32);
		for (int i = 0; i < objects.length; i++) {
			if (i > 0 && separator != null) {
				buffer.append(separator);
			}
			buffer.append(objects[i].toString());
		}

		return buffer.toString();
	}

	public static NSArray<String> componentsSeparatedByString(final String string, final String separator) {
		NSMutableArray<String> objects;
		if (string == null || string.length() == 0) {
			return emptyArray();
		}
		final int stringLength = string.length();

		if (separator == null || separator.length() == 0) {
			return new NSArray<>(string);
		}
		final int separatorLength = separator.length();

		int start = 0;
		int index = 0;
		int count = 0;

		if (separatorLength == 1 && stringLength < 256) {
			final char[] parseData = string.toCharArray();
			final char charSeparator = separator.charAt(0);

			for (int i = 0; i < stringLength; ++i) {
				if (parseData[i] == charSeparator) {
					++count;
				}
			}

			if (count == 0) {
				return new NSArray<>(string);
			}

			objects = new NSMutableArray<>(count + 1);
			final int end = stringLength - 1;
			for (index = 0; index <= end; ++index) {
				if (parseData[index] == charSeparator) {
					if (start == index) {
						objects.addObject("");
					} else {
						objects.addObject(string.substring(start, index));
					}
					start = index + 1;
				}
			}
			if (parseData[end] == charSeparator) {
				objects.addObject("");
			} else {
				objects.addObject(string.substring(start, stringLength));
			}
		} else {
			objects = new NSMutableArray<>(4);
			final int end = stringLength - separatorLength;
			while (true) {
				if (start >= stringLength) {
					return objects;
				}
				index = string.indexOf(separator, start);

				if (index < 0) {
					index = stringLength;
				}

				if (index == end) {
					break;
				}
				objects.addObject(string.substring(start, index));
				start = index + separatorLength;
			}
			if (start <= index) {
				objects.addObject(string.substring(start, index));
			}

			objects.addObject("");
		}

		return objects;
	}

	public static NSMutableArray<String> _mutableComponentsSeparatedByString(final String string,
			final String separator) {
		return componentsSeparatedByString(string, separator).mutableClone();
	}

	private Object _valueForKeyPathWithOperator(final String keyPath) {
		final int index = keyPath.indexOf('.');
		String operatorName;
		String operatorPath;
		if (index < 0) {
			operatorName = keyPath.substring(1);
			operatorPath = "";
		} else {
			operatorName = keyPath.substring(1, index);
			operatorPath = index >= keyPath.length() - 1 ? "" : keyPath.substring(index + 1);
		}
		final Operator arrayOperator = operatorForKey(operatorName);
		if (arrayOperator != null) {
			return arrayOperator.compute(this, operatorPath);
		}
		throw new IllegalArgumentException("No key operator available to compute aggregate " + keyPath);
	}

	@Override
	public Object valueForKey(final String key) {
		if (key != null) {
			if (key.charAt(0) == _OperatorIndicatorChar) {
				return _valueForKeyPathWithOperator(key);
			}
			if (CountOperatorName.equals(key)) {
				return _NSUtilities.IntegerForInt(count());
			}
		}
		final Object[] objects = objectsNoCopy();
		final NSMutableArray<Object> values = new NSMutableArray<>(objects.length);
		for (final Object object : objects) {
			final Object value = NSKeyValueCodingAdditions.Utility.valueForKeyPath(object, key);
			values.addObject(value == null ? (Object) NSKeyValueCoding.NullValue : value);
		}

		return values;
	}

	@Override
	public void takeValueForKey(final Object value, final String key) {
		final Object[] objects = objectsNoCopy();
		for (final Object object : objects) {
			NSKeyValueCodingAdditions.Utility.takeValueForKeyPath(object, value, key);
		}

	}

	@Override
	public Object valueForKeyPath(final String keyPath) {
		if (keyPath != null && keyPath.charAt(0) == _OperatorIndicatorChar) {
			return _valueForKeyPathWithOperator(keyPath);
		}
		return NSKeyValueCodingAdditions.DefaultImplementation.valueForKeyPath(this, keyPath);
	}

	@Override
	public void takeValueForKeyPath(final Object value, final String keyPath) {
		NSKeyValueCodingAdditions.DefaultImplementation.takeValueForKeyPath(this, value, keyPath);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class classForCoder() {
		return _CLASS;
	}

	public static Object decodeObject(final NSCoder coder) {
		return new NSArray<>(coder.decodeObjects());
	}

	@Override
	public void encodeWithCoder(final NSCoder coder) {
		coder.encodeObjects(objectsNoCopy());
	}

	public void makeObjectsPerformSelector(final NSSelector selector, final Object... parameters) {
		if (selector == null) {
			throw new IllegalArgumentException("Selector cannot be null");
		}
		final Object[] objects = objectsNoCopy();
		for (final Object object : objects) {
			NSSelector._safeInvokeSelector(selector, object, parameters);
		}

	}

	@Override
	public int _shallowHashCode() {
		return _NSArrayClassHashCode;
	}

	@Override
	public int hashCode() {
		if (_mustRecomputeHash()) {
			int hash = 0;
			final int max = count() <= 16 ? count() : 16;
			for (int i = 0; i < max; i++) {
				final Object element = objectAtIndex(i);
				if (element instanceof _NSFoundationCollection) {
					hash ^= ((_NSFoundationCollection) element)._shallowHashCode();
				} else {
					hash ^= element.hashCode();
				}
			}

			_hashCache = hash;
			_setMustRecomputeHash(false);
		}
		return _hashCache;
	}

	@Override
	public Object clone() {
		return this;
	}

	public NSArray<E> immutableClone() {
		return this;
	}

	public NSMutableArray<E> mutableClone() {
		return new NSMutableArray<>(this);
	}

	@Override
	public String toString() {
		if (count() == 0) {
			return "()";
		}
		final StringBuilder buffer = new StringBuilder(128);
		buffer.append('(');
		final Object[] objects = objectsNoCopy();
		for (int i = 0; i < objects.length; i++) {
			final Object object = objects[i];
			if (i > 0) {
				buffer.append(", ");
			}
			if (object instanceof String) {
				buffer.append('"');
				buffer.append((String) object);
				buffer.append('"');
				continue;
			}
			if (object instanceof Boolean) {
				buffer.append(((Boolean) object).booleanValue() ? "true" : "false");
			} else {
				buffer.append(object == this ? "THIS" : object.toString());
			}
		}

		buffer.append(')');
		return buffer.toString();
	}

	protected boolean _mustRecomputeHash() {
		return _recomputeHashCode;
	}

	protected void _setMustRecomputeHash(final boolean change) {
		_recomputeHashCode = change;
	}

	private void writeObject(final ObjectOutputStream s) throws IOException {
		final java.io.ObjectOutputStream.PutField fields = s.putFields();
		fields.put(SerializationValuesFieldKey, objects());
		s.writeFields();
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		final java.io.ObjectInputStream.GetField fields = s.readFields();
		Object[] values = (Object[]) fields.get(SerializationValuesFieldKey, _NSUtilities._NoObjectArray);
		values = values != null ? values : _NSUtilities._NoObjectArray;
		initFromObjects(values, 0, values.length, true, false);
	}

	private Object readResolve() throws ObjectStreamException {
		if (getClass() == _CLASS && count() == 0) {
			return EmptyArray;
		}
		return this;
	}

	@Override
	public void add(final int index, final E element) {
		throw new UnsupportedOperationException(
				"add is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean add(final E element) {
		throw new UnsupportedOperationException(
				"add is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean addAll(final Collection<? extends E> collection) {
		throw new UnsupportedOperationException(
				"addAll is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean addAll(final int index, final Collection<? extends E> collection) {
		throw new UnsupportedOperationException(
				"addAll is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean contains(final Object element) {
		if (element == null) {
			throw new NullPointerException("NSArray does not support null values");
		}
		return containsObject(element);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Iterator<E> iterator() {
		return new _NSJavaArrayListIterator(objectsNoCopy(), count());
	}

	@Override
	public Object[] toArray() {
		return objects();
	}

	@Override
	public <T> T[] toArray(T[] objects) {
		if (objects == null) {
			throw new NullPointerException("List.toArray() cannot have a null parameter");
		}

		final int count = count();
		if (count <= 0) {
			return objects;
		}

		final Object[] objs = objectsNoCopy();
		if (objects.length < objs.length) {
			objects = (T[]) java.lang.reflect.Array.newInstance(objects.getClass().getComponentType(), objs.length);
		}
		System.arraycopy(objs, 0, objects, 0, objs.length);
		return objects;
	}

	@Override
	public boolean containsAll(final Collection<?> c) {
		final Object[] objects = c.toArray();
		if (objects.length > 0) {
			for (final Object object : objects) {
				if (object == null || _findObjectInArray(0, count(), object, false) == NotFound) {
					return false;
				}
			}

		}
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ListIterator<E> listIterator() {
		final Object[] objs = objectsNoCopy();
		return new _NSJavaArrayListIterator(objs, count());
	}

	@Override
	@SuppressWarnings("unchecked")
	public ListIterator<E> listIterator(final int index) {
		final Object[] objs = objectsNoCopy();
		return new _NSJavaArrayListIterator(objs, count(), index);
	}

	@Override
	public E get(final int index) {
		if (index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
		}
		return objectAtIndex(index);
	}

	@Override
	public E set(final int index, final E element) {
		if (index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
		}
		throw new UnsupportedOperationException("Set is not a support operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public int indexOf(final Object element) {
		if (element == null) {
			throw new NullPointerException("com.webobjects.foundation.NSArray does not support null values");
		}
		return indexOfObject(element);
	}

	@Override
	public int lastIndexOf(final Object element) {
		int lastIndex = NotFound;
		if (element == null) {
			throw new NullPointerException("com.webobjects.foundation.NSArray does not support null values");
		}
		for (int i = 0; i < count(); i++) {
			if (objectAtIndex(i).equals(element)) {
				lastIndex = i;
			}
		}

		return lastIndex;
	}

	@Override
	public boolean isEmpty() {
		return count() == 0;
	}

	@Override
	public int size() {
		return count();
	}

	@Override
	public E remove(final int index) {
		throw new UnsupportedOperationException(
				"Remove is not a support operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean remove(final Object object) {
		throw new UnsupportedOperationException(
				"Remove is not a support operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException(
				"Clear is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean retainAll(final Collection<?> collection) {
		throw new UnsupportedOperationException(
				"RetainAll is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public boolean removeAll(final Collection<?> collection) {
		throw new UnsupportedOperationException(
				"RemoveAll is not a supported operation in com.webobjects.foundation.NSArray");
	}

	@Override
	public List<E> subList(final int fromIndex, final int toIndex) {
		if (fromIndex < 0 || toIndex > count() || fromIndex > toIndex) {
			throw new IndexOutOfBoundsException(
					"Illegal index value (fromIndex < 0 || toIndex > size || fromIndex > toIndex)");
		}
		return subarrayWithRange(new NSRange(fromIndex, toIndex - fromIndex));
	}

	public static final <T> NSArray<T> emptyArray() {
		return EmptyArray;
	}

	/**
	 * Returns an immutable empty {@code NSArray}.
	 *
	 * @param <E> the {@code NSArray}'s element type
	 * @return an empty {@code NSArray}
	 */
	public static <E> NSArray<E> of() {
		return EmptyArray;
	}

	/**
	 * Returns an immutable {@code NSArray} containing one element.
	 *
	 * @param <E>     the {@code NSArray}'s element type
	 * @param element the element to be contained in the array
	 * @return a {@code NSArray} containing the specified element
	 */
	public static <E> NSArray<E> of(final E element) {
		return new NSArray<>(element);
	}

	/**
	 * Returns an immutable {@code NSArray} containing an arbitrary number of
	 * elements.
	 *
	 * @param <E>      the {@code NSArray}'s element type
	 * @param elements the elements to be contained in the array
	 * @return a {@code NSArray} containing the specified elements
	 */
	@SafeVarargs
	public static <E> NSArray<E> of(final E... elements) {
		if (elements.length == 0) {
			return EmptyArray;
		}
		if (elements.length == 1) {
			return new NSArray(elements[0]);
		}

		return new NSArray<>(elements);
	}

	static {
		try {
			setOperatorForKey(CountOperatorName, new _CountOperator());
			setOperatorForKey(MaximumOperatorName, new _MaxOperator());
			setOperatorForKey(MinimumOperatorName, new _MinOperator());
			setOperatorForKey(SumOperatorName, new _SumNumberOperator());
			setOperatorForKey(AverageOperatorName, new _AvgNumberOperator());
		} catch (final Throwable e) {
			NSLog.err.appendln("Exception occurred in initializer");
			if (NSLog.debugLoggingAllowedForLevel(1)) {
				NSLog.debug.appendln(e);
			}
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}
}
