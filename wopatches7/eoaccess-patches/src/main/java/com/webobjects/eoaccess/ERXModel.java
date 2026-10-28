package com.webobjects.eoaccess;

import java.net.URL;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * This EOModel subclass primarily provides the opportunity to subclass
 * EOEntity.
 *
 * <p>
 * <b>Note</b> the package <code>com.webobjects.eoaccess</code> is used to allow
 * any protected or default access superclass instance methods to resolve at
 * runtime.
 *
 * <p>
 * To allow for extended prototypes set
 * <code>er.extensions.ERXModel.useExtendedPrototypes=true</code>. Note: this
 * may be incompatible with
 * {@link er.extensions.eof.ERXModelGroup#flattenPrototypes}.
 * </p>
 *
 * <p>
 * The existence of prototype entities based on specific conventions is checked
 * and the attributes of those prototype entities are added to the model's
 * prototype attributes cache in a specific order. The search order ensures that
 * the same prototype attribute names in different prototype entities get chosen
 * in a predictable way.
 * </p>
 *
 * <p>
 * Consequently, you can use this search order knowledge to over-ride Wonder's
 * ERPrototypes for your entire set of application eomodels or just for specific
 * named eomodels.
 * </p>
 *
 * To understand the variables used in deriving the prototype entity names that
 * are searched a few definitions are appropriate
 * <dl>
 * <dt>&lt;pluginName&gt;</dt>
 * <dd>Relates to the database type. Examples of pluginName are MySQL, Derby,
 * FrontBase, OpenBase, Oracle, Postgresql</dd>
 * <dt>&lt;adaptorName&gt;</dt>
 * <dd>Relates to the general persistence mechanism. Examples of adaptorName are
 * JDBC, Memory, REST</dd>
 * <dt>&lt;modelName&gt;</dt>
 * <dd>The name of an eomodel in your app or frameworks</dd>
 *
 * </dl>
 *
 * The priority order (which is basically the reverse of the search order) for
 * prototype entities is as follows:
 * <ul>
 * <li>EOJDBC&lt;pluginName&gt;&lt;modelname&gt;Prototypes</li>
 * <li>EO&lt;adaptorName&gt;&lt;modelname&gt;Prototypes</li>
 * <li>EO&lt;modelname&gt;Prototypes</li>
 * <li>EOJDBC&lt;pluginName&gt;CustomPrototypes</li>
 * <li>EO&lt;adaptorName&gt;CustomPrototypes</li>
 * <li>EOCustomPrototypes</li>
 * <li>EOJDBC&lt;pluginName&gt;Prototypes <em>(Available for popular databases
 * in ERPrototypes framework)</em></li>
 * <li>EO&lt;adaptorName&gt;Prototypes <em>(ERPrototypes has some of these too
 * for generic-JDBC, Memory, etc.)</em></li>
 * <li>EOPrototypes <em>(Without ERXModel and the extendedPrototypes, this was
 * pretty much your only way to add your own prototypes alongside
 * ERPrototypes)</em></li>
 * </ul>
 *
 * @author ldeck
 */
public class ERXModel extends EOModel {
	// Expose EOModel._EOGlobalModelLock so that ERXModelGroup can lock on it
	public static Object _ERXGlobalModelLock = EOModel._EOGlobalModelLock;

	/**
	 * Utility to add attributes to the prototype cache. As the attributes are
	 * chosen by name, replace already existing ones.
	 *
	 * @param model            - the model to which the prototype attributes will be
	 *                         cached
	 * @param prototypesEntity - the entity from which to copy the prototype
	 *                         attributes
	 */
	private static void addAttributesToPrototypesCache(final EOModel model, final EOEntity prototypesEntity) {
		if (model != null && prototypesEntity != null) {
			addAttributesToPrototypesCache(model, attributesFromEntity(prototypesEntity));
		}
	}

	/**
	 * Utility to add attributes to the prototype cache for a given model. As the
	 * attributes are chosen by name, replace already existing ones.
	 *
	 * @param model               - the model to which the prototype attributes will
	 *                            be cached
	 * @param prototypeAttributes - the prototype attributes to add to the model
	 */
	private static void addAttributesToPrototypesCache(final EOModel model,
			final NSArray<? extends EOAttribute> prototypeAttributes) {
		if (model != null && prototypeAttributes.count() != 0) {
			final NSArray keys = namesForAttributes(prototypeAttributes);
			final NSDictionary temp = new NSDictionary(prototypeAttributes, keys);
			model._prototypesByName.addEntriesFromDictionary(temp);
		}
	}

	/**
	 * Utility for getting all the attributes off an entity. If the entity is null,
	 * an empty array is returned.
	 *
	 * @param entity an entity
	 * @return array of attributes from the given entity
	 */
	private static NSArray<EOAttribute> attributesFromEntity(final EOEntity entity) {
		NSArray<EOAttribute> result = NSArray.emptyArray();
		if (entity != null) {
			result = entity.attributes();
		}
		return result;
	}

	/**
	 * Create the prototype cache for the given model by walking a search order.
	 *
	 * @param model
	 */
	public static void createPrototypes(final ERXModel model) {
		// Remove password for logging
		final NSMutableDictionary dict = model.connectionDictionary().mutableClone();
		if (dict.objectForKey("password") != null) {
			dict.setObjectForKey("<deleted for log>", "password");
		}
		NSLog.out.appendln("Creating prototypes for model: " + model.name() + "->" + dict);
		synchronized (_EOGlobalModelLock) {
			final StringBuilder debugInfo = new StringBuilder();
			final boolean debugEnabled = NSLog.debugLoggingAllowedForLevel(NSLog.DebugLevelDetailed);
			if (debugEnabled) {
				debugInfo.append("Model = " + model.name());
			}
			model._prototypesByName = new NSMutableDictionary();
			final String name = model.name();
			NSArray adaptorPrototypes = NSArray.EmptyArray;
			final EOAdaptor adaptor = EOAdaptor.adaptorWithModel(model);
			try {
				adaptorPrototypes = adaptor.prototypeAttributes();
			} catch (final Exception e) {
				NSLog.err.appendln("Could not get prototype attributes from adaptor.");
				NSLog.err.appendln(e);
			}
			addAttributesToPrototypesCache(model, adaptorPrototypes);
			final NSArray prototypesToHide = attributesFromEntity(model._group.entityNamed("EOPrototypesToHide"));
			model._prototypesByName.removeObjectsForKeys(namesForAttributes(prototypesToHide));

			String plugin = null;
			// Don't pull in JDBCAdaptor framework for this, just check the name.
			// if (adaptor instanceof JDBCAdaptor &&
			// !"erprototypes".equalsIgnoreCase(model.name())) {
			if ("JDBC".equals(model.adaptorName()) && !"erprototypes".equalsIgnoreCase(model.name())) {
				plugin = (String) model.connectionDictionary().objectForKey("plugin");
				// Just define the plugin in the connection dictionary, wtf?
//				if (plugin == null) {
//					plugin = ERXEOAccessUtilities.guessPluginName(model);
//				} // ~ if (plugin == null)
				if (plugin != null && plugin.toLowerCase().endsWith("plugin")) {
					plugin = plugin.substring(0, plugin.length() - "plugin".length());
				}
				if (debugEnabled) {
					debugInfo.append("; plugin = " + plugin);
				}
			}

			addAttributesToPrototypesCache(model, model._group.entityNamed("EOPrototypes"));
			addAttributesToPrototypesCache(model, model._group.entityNamed("EO" + model.adaptorName() + "Prototypes"));
			if (debugEnabled) {
				debugInfo.append(
						"; Prototype Entities Searched = EOPrototypes, " + "EO" + model.adaptorName() + "Prototypes");
			}
			if (plugin != null) {
				addAttributesToPrototypesCache(model, model._group.entityNamed("EOJDBC" + plugin + "Prototypes"));
				if (debugEnabled) {
					debugInfo.append(", " + "EOJDBC" + plugin + "Prototypes");
				}
			}

			addAttributesToPrototypesCache(model, model._group.entityNamed("EOCustomPrototypes"));
			addAttributesToPrototypesCache(model,
					model._group.entityNamed("EO" + model.adaptorName() + "CustomPrototypes"));
			if (debugEnabled) {
				debugInfo.append(", EOCustomPrototypes, " + "EO" + model.adaptorName() + "CustomPrototypes");
			}
			if (plugin != null) {
				addAttributesToPrototypesCache(model, model._group.entityNamed("EOJDBC" + plugin + "CustomPrototypes"));
				if (debugEnabled) {
					debugInfo.append(", " + "EOJDBC" + plugin + "CustomPrototypes");
				}
			}

			addAttributesToPrototypesCache(model, model._group.entityNamed("EO" + name + "Prototypes"));
			addAttributesToPrototypesCache(model,
					model._group.entityNamed("EO" + model.adaptorName() + name + "Prototypes"));
			if (debugEnabled) {
				debugInfo.append(
						", " + "EO" + name + "Prototypes" + ", " + "EO" + model.adaptorName() + name + "Prototypes");
			}
			if (plugin != null) {
				addAttributesToPrototypesCache(model,
						model._group.entityNamed("EOJDBC" + plugin + name + "Prototypes"));
				if (debugEnabled) {
					debugInfo.append(", " + "EOJDBC" + plugin + name + "Prototypes");
				}
			}

			if (debugEnabled) {
				NSLog.out.appendln(debugInfo.toString());
			}
		}
	}

	/**
	 * Utility for getting all names from an array of attributes.
	 *
	 * @param attributes array of attributes
	 * @return array of attribute names
	 */
	private static NSArray<String> namesForAttributes(final NSArray<? extends EOAttribute> attributes) {
		return (NSArray<String>) attributes.valueForKey("name");
	}

	/**
	 * Defaults to false. Note: when enabled, this may be incompatible with
	 * {@link er.extensions.eof.ERXModelGroup#flattenPrototypes}.
	 *
	 * @return the boolean property value for
	 *         <code>er.extensions.ERXModel.useExtendedPrototypes</code>.
	 */
	public static boolean isUseExtendedPrototypesEnabled() {
		return Boolean.getBoolean("er.extensions.ERXModel.useExtendedPrototypes");
	}

	/**
	 * Creates and returns a new ERXModel.
	 */
	public ERXModel() {
	}

	/**
	 * Creates a new EOModel object by reading the contents of the model archive at
	 * url. Sets the EOModel's name and path from the context of the model archive.
	 * Throws an IllegalArgumentException if url is null or if unable to read
	 * content from url. Throws a runtime exception if unable for any other reason
	 * to initialize the model from the specified java.net.URL; the error text
	 * indicates the nature of the exception.
	 *
	 * @param url - The java.net.URL to a model archive.
	 */
	public ERXModel(final URL url) {
		super(url);
	}

	/**
	 * @param propertyList
	 * @param path
	 */
	public ERXModel(final NSDictionary propertyList, final String path) {
		super(propertyList, path);
	}

	/**
	 * @param propertyList
	 * @param url
	 */
	public ERXModel(final NSDictionary propertyList, final URL url) {
		super(propertyList, url);
	}

	/**
	 * Sets the default EOEntity class to com.webobjects.eoaccess.ERXEntity. You can
	 * provide your own via the property
	 * <code>er.extensions.ERXModel.defaultEOEntityClassName</code> however your
	 * class must be in the same package unless you plan on re-implementing eof
	 * itself.
	 *
	 * @see com.webobjects.eoaccess.EOModel#_addEntityWithPropertyList(java.lang.Object)
	 */
	@Override
	public Object _addEntityWithPropertyList(final Object propertyList)
			throws InstantiationException, IllegalAccessException {
		final NSMutableDictionary<String, Object> list = ((NSDictionary<String, Object>) propertyList).mutableClone();
		if (list.objectForKey("entityClass") == null) {
			String eoEntityClassName = System.getProperty("er.extensions.ERXModel.defaultEOEntityClassName");
			if (eoEntityClassName == null) {
				eoEntityClassName = ERXEntity.class.getName();
			}
			list.setObjectForKey(eoEntityClassName, "entityClass");
		}
		return super._addEntityWithPropertyList(list);
	}

	/**
	 * Overridden to use our prototype creation method if
	 * <code>er.extensions.ERXModel.useExtendedPrototypes=true</code>.
	 */
	@Override
	public NSArray availablePrototypeAttributeNames() {
		synchronized (_EOGlobalModelLock) {
			if (_prototypesByName == null && useExtendedPrototypes()) {
				createPrototypes(this);
			}
		}
		return super.availablePrototypeAttributeNames();
	}

	/**
	 * Overridden to use our prototype creation method if
	 * <code>er.extensions.ERXModel.useExtendedPrototypes=true</code>.
	 */
	@Override
	public EOAttribute prototypeAttributeNamed(final String name) {
		synchronized (_EOGlobalModelLock) {
			if (_prototypesByName == null && useExtendedPrototypes()) {
				createPrototypes(this);
			}
		}
		return super.prototypeAttributeNamed(name);
	}

	// This should be unnecessary, it's already being called after setModelGroup in
	// addModel in ERXModelGroup.
//	@Override
//	public void setModelGroup(EOModelGroup modelGroup) {
//		super.setModelGroup(modelGroup);
//		if (modelGroup instanceof ERXModelGroup) {
//			((ERXModelGroup) modelGroup).resetConnectionDictionaryInModel(this);
//		}
//	}

	/**
	 * Defaults to false as returned by {@link #isUseExtendedPrototypesEnabled()}.
	 *
	 * @return <code>true</code> if extended prototypes are used
	 * @see #isUseExtendedPrototypesEnabled()
	 */
	protected boolean useExtendedPrototypes() {
		return isUseExtendedPrototypesEnabled();
	}

}
