package com.webobjects.eoaccess;

import java.util.Collection;

import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;

/**
 * Creates ordering based on foreign key dependencies.
 *
 * @author chill
 */
public class ERXEntityFKConstraintOrder extends ERXEntityOrder {

	/**
	 * Designated constructor for implementing classes.
	 *
	 * @param modelGroup EOModelGroup to get list of all entities from
	 */
	public ERXEntityFKConstraintOrder(final EOModelGroup modelGroup) {
		super(modelGroup);
	}

	/**
	 * Convenience constructor for implementing classes. Uses
	 * <code>EOModelGroup.defaultGroup()</code>.
	 */
	public ERXEntityFKConstraintOrder() {
	}

	/**
	 * Processes the list of entities, creating the ordering dictionary based on
	 * foreign key constraints.
	 *
	 * @return a dictionary keyed on dependencyKeyFor(EOEntity)
	 */
	@Override
	protected NSDictionary dependenciesByEntity() {
		log("Building dependency list");

		final NSMutableDictionary dependencyList = new NSMutableDictionary(allEntities().count());
		for (final Object element : allEntities()) {
			final EOEntity entity = (EOEntity) element;
			log("Finding dependencies of " + entity.name());

			for (final Object element2 : entity.relationships()) {
				final EORelationship relationship = (EORelationship) element2;

				if (hasForeignKeyConstraint(relationship)) {
					final EOEntity destinationEntity = relationship.destinationEntity();
					log("Recording dependency on " + destinationEntity.name());
					entitiesDependentOn(dependencyList, destinationEntity).addObject(entity.name());
				} else {
					log("Ignoring, is not FK relationship or vertical inheritance parent");
				}
			}
		}
		log("Finished building dependency list");

		if (NSLog.debugLoggingAllowedForLevel(NSLog.DebugLevelDetailed)) {
			for (int i = 0; i < allEntities().count(); i++) {
				final EOEntity entity = allEntities().objectAtIndex(i);
				log("Entity " + entity.name() + " is referenced by " + entitiesDependentOn(dependencyList, entity));
			}
		}

		return dependencyList;
	}

	/**
	 * @param relationship EORelationship to test
	 * @return <code>true</code> if relationship models a relation that will have a
	 *         foreign key constraint in the database
	 */
	protected boolean hasForeignKeyConstraint(final EORelationship relationship) {
		log("Examining relationshp " + relationship.name());

		// Reflexive relationships (circular dependencies) can't be accommodated by
		// entity ordering,
		// these require ordering within the operations for an entity. Check the
		// externalName() rather than
		// entity name so that it will handle relationships to a super or subclass in a
		// Single-Table inheritance structure
		if (relationship.entity().externalName() != null &&
				relationship.entity().externalName().equals(relationship.destinationEntity().externalName())) {
			log("Ignoring: reflexive relationship");
			return false;
		}

		if (!arraysAreIdenticalSets(relationship.destinationAttributes(),
				relationship.destinationEntity().primaryKeyAttributes())) {
			log("No FK constraint: found non-PK attributes in destination");
			return false;
		}
		if (arraysAreIdenticalSets(relationship.sourceAttributes(),
				relationship.entity().primaryKeyAttributes())) {
			// PK - PK relationships for vertical inheritance (child to parent) also need to
			// be considered in ordering
			if (relationship.destinationEntity().equals(relationship.entity().parentEntity())) {
				log("Is vertical inheritance PK to PKconstraint");
				return true;
			}

			// Bug? Do these need to be included?
			log("No FK constraint: Is PK to PK");
			return false;
		}

		log("Is FK constraint");
		return true;
	}

	/**
	 * This implementation returns <code>entity.externalName()</code> as the
	 * dependcy is actually on tables not EOEntities .
	 *
	 * @param entity EOEntity to return key into dependency dictionary for
	 *
	 * @return key for <code>entity</code> into dependency dictionary returned by
	 *         <code>dependenciesByEntity()</code>
	 */
	@Override
	protected String dependencyKeyFor(final EOEntity entity) {
		if (entity.externalName() == null) {
			return "Abstract Dummy Entity";
		}
		return entity.externalName();
	}

	/**
	 * Returns the list of the names of the entities that reference (depend on) this
	 * entity. This list is populated by <code>builddependencyList()</code>. If
	 * <code>builddependencyList()</code> has not finished executing, the list
	 * returned by this method may not be complete.
	 *
	 * @param dependencies list of dependencies being built by
	 *                     <code>builddependencyList()</code>
	 * @param entity       EOEntity to return list of referencing entities for
	 * @return list of names of entities previously recorded as referencing this
	 *         entity
	 */
	protected NSMutableSet entitiesDependentOn(final NSMutableDictionary dependencies, final EOEntity entity) {
		NSMutableSet referencingEntities = (NSMutableSet) dependencies.objectForKey(dependencyKeyFor(entity));
		if (referencingEntities == null) {
			referencingEntities = new NSMutableSet();
			dependencies.setObjectForKey(referencingEntities, dependencyKeyFor(entity));
		}
		return referencingEntities;
	}

	/**
	 * Simple comparison method to see if two array objects are identical sets.
	 *
	 * @param array1 first array
	 * @param array2 second array
	 * @return result of comparison
	 */
	private static <T> boolean arraysAreIdenticalSets(final Collection<? super T> array1,
			final Collection<? super T> array2) {
		if (array1 == null || array2 == null) {
			return array1 == array2;
		}

		for (final Object item : array1) {
			if (!array2.contains(item)) {
				return false;
			}
		}

		for (final Object item : array2) {
			if (!array1.contains(item)) {
				return false;
			}
		}

		return true;
	}

	private static void log(final String str) {
		if (NSLog.debugLoggingAllowedForLevel(NSLog.DebugLevelDetailed)) {
			NSLog.out.appendln(str);
		}
	}
}
