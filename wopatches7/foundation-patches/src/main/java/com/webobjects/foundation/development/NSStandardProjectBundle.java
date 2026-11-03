package com.webobjects.foundation.development;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSValueUtilities;

/**
 * This class is patched solely to fix {@link Factory#createBundleFromProjectFolder(File)}'s
 * Maven-vs-"Fluffy Bunny" project-layout detection, which is based entirely on a single
 * Eclipse {@code .project} nature id and has proven fragile across ERFoundation versions:
 *
 * <ul>
 * <li>ERFoundation 1.0 checks for the long-dead, pre-2011 "m2eclipse" nature id
 * {@code org.maven.ide.eclipse.maven2Nature}. No current Eclipse/m2e installation ever
 * writes that nature, so under 1.0 this check is effectively dead code: every project,
 * including genuinely Maven-standard-layout ones, falls through to
 * {@link NSFluffyBunnyProjectBundle}.</li>
 * <li>ERFoundation 1.2 updated the check to the modern nature id
 * {@code org.eclipse.m2e.core.maven2Nature}. That nature is present on essentially every
 * current Eclipse/m2e-managed project &mdash; <em>including</em> projects that are
 * m2e-managed (for dependency resolution) but still use the classic
 * {@code Sources}/{@code Resources}/{@code Components}/{@code WebServerResources}
 * ("Fluffy Bunny") physical layout rather than {@code src/main/java}. Such projects now get
 * misclassified as {@link NSMavenProjectBundle}, which only searches {@code src/main/*}-style
 * paths for {@code Info.plist}/{@code Properties}. The resulting bundle never resolves
 * {@code NSExecutable}, <code>NSBundle.mainBundle()</code>/<code>_mainBundleName</code> never
 * get populated, and startup fails with
 * {@code NullPointerException: Cannot invoke "String.toLowerCase()" because "mainBundleName" is null}
 * out of {@code ERXApplication$Loader.collectMainProps}.</li>
 * </ul>
 *
 * Rather than trust either nature id (both are just Eclipse IDE metadata, unrelated to the
 * project's actual on-disk layout, and subject to drifting out of sync with it &mdash; exactly
 * what happened here), this patch decides Maven vs. Fluffy Bunny primarily from the project
 * folder's <em>actual physical layout</em>:
 *
 * <ol>
 * <li>If the project has a {@code Resources} folder together with a {@code Components} and/or
 * {@code WebServerResources} folder, it's Fluffy Bunny &mdash; regardless of any Maven nature.</li>
 * <li>Otherwise, if it has a {@code src/main/java}, {@code src/main/resources}, or
 * {@code src/main/woresources} folder, it's Maven-standard-layout.</li>
 * <li>Otherwise (neither physical marker is present, e.g. a brand-new/empty project), fall back
 * to the original nature-id check, recognizing <em>both</em> the legacy and modern maven2Nature
 * ids so this keeps working regardless of which m2e vintage wrote the {@code .project} file.</li>
 * </ol>
 *
 * Everything else in this class is unchanged from ERFoundation 1.2.
 */
public abstract class NSStandardProjectBundle extends NSProjectBundle {
	private NSDictionary<String, Object> _infoDictionary;
	private Properties _buildProperties;
	private Document _eclipseProjectDocument;

	public NSStandardProjectBundle(String projectPath, Properties buildProperties, Document eclipseProject) {
		super(projectPath);
		this._buildProperties = buildProperties;
		this._eclipseProjectDocument = eclipseProject;
	}

	@Override
	public NSDictionary<String, Object> infoDictionary() {
		NSDictionary infoDictionary = this._infoDictionary;
		if (infoDictionary == null) {
			infoDictionary = NSDictionary.emptyDictionary();
			Boolean isApplication = null;
			String projectName = null;
			String principalClass = null;
			String eoAdaptorClass = null;
			String dependencies = null;
			if (this._buildProperties != null) {
				projectName = this._buildProperties.getProperty("framework.name");
				if (projectName != null) {
					isApplication = Boolean.FALSE;
				} else {
					projectName = this._buildProperties.getProperty("application.name");
					if (projectName != null) {
						isApplication = Boolean.TRUE;
					} else {
						projectName = this._buildProperties.getProperty("project.name");
						if ("framework".equals(this._buildProperties.getProperty("project.type"))) {
							isApplication = Boolean.FALSE;
						} else if ("application".equals(this._buildProperties.getProperty("project.type"))) {
							isApplication = Boolean.TRUE;
						}
					}
				}
				principalClass = this._buildProperties.getProperty("principalClass");
				eoAdaptorClass = this._buildProperties.getProperty("eoAdaptorClassName");
				dependencies = this._buildProperties.getProperty("dependencies");
			}
			if (this._eclipseProjectDocument != null) {
				Element projectDescriptionElement;
				NodeList nameList;
				if (projectName == null && (nameList = (projectDescriptionElement = this._eclipseProjectDocument.getDocumentElement()).getElementsByTagName("name")).getLength() > 0) {
					projectName = ((Element) nameList.item(0)).getFirstChild().getNodeValue();
				}
				if (isApplication == null) {
					projectDescriptionElement = this._eclipseProjectDocument.getDocumentElement();
					NodeList natureNodeList = projectDescriptionElement.getElementsByTagName("nature");
					int i = 0;
					while (i < natureNodeList.getLength()) {
						String nature = ((Element) natureNodeList.item(i)).getFirstChild().getNodeValue();
						if (nature != null) {
							if (nature.startsWith("org.objectstyle.wolips.") && nature.endsWith("applicationnature")) {
								isApplication = Boolean.TRUE;
							} else if (nature.startsWith("org.objectstyle.wolips.") && nature.endsWith("frameworknature")) {
								isApplication = Boolean.FALSE;
							}
						}
						++i;
					}
				}
			}
			if (projectName == null) {
				projectName = new File(this.projectPath()).getName();
				System.err.println("NSBundle: Unable to determine the project name for '" + this.projectPath() + "'. Guessing '" + projectName + "'.");
			}
			if (isApplication == null) {
				isApplication = Boolean.FALSE;
				System.err.println("NSBundle: Unable to determine the project type for '" + this.projectPath() + "'. Guessing it is a framework.");
			}
			NSMutableDictionary mockInfoPlist = new NSMutableDictionary();
			mockInfoPlist.setObjectForKey((Object) projectName, (Object) "NSExecutable");
			mockInfoPlist.setObjectForKey((Object) "webo", (Object) "CFBundleSignatureKey");
			if (isApplication.booleanValue()) {
				mockInfoPlist.setObjectForKey((Object) "APPL", (Object) "CFBundlePackageType");
			} else {
				mockInfoPlist.setObjectForKey((Object) "FMWK", (Object) "CFBundlePackageType");
			}
			if (principalClass != null) {
				mockInfoPlist.setObjectForKey((Object) principalClass, (Object) "NSPrincipalClass");
			}
			if (eoAdaptorClass != null) {
				mockInfoPlist.setObjectForKey((Object) eoAdaptorClass, (Object) "EOAdaptorClassName");
			}
			mockInfoPlist.setObjectForKey((Object) "true", (Object) "Has_WOComponents");
			NSMutableArray requiredBundleNames = new NSMutableArray();
			if (dependencies != null) {
				for (String dependency : NSArray.componentsSeparatedByString((String) dependencies, (String) ",")) {
					requiredBundleNames.addObject((Object) dependency.trim());
				}
			}
			mockInfoPlist.setObjectForKey((Object) "1.2.3", (Object) "CFBundleShortVersionString");
			mockInfoPlist.setObjectForKey((Object) "0", (Object) "Implementation-Version");
			this._infoDictionary = infoDictionary = mockInfoPlist;
		}
		return infoDictionary;
	}

	public static class Factory extends NSBundleFactory {
		private static Properties _primordialProperties;
		private static NSArray<String> _projectSearchPaths;

		public NSBundle bundleForPath(String path, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle;
			block10: {
				String bundleName;
				block14: {
					block13: {
						block12: {
							NSBundle existingBundle;
							String normalizedPath;
							block11: {
								bundle = this._bundleForPath(path, shouldCreateBundle, newIsJar);
								if (bundle != null) break block10;
								normalizedPath = NSBundle._normalizeExistingBundlePath(path);
								existingBundle = NSBundle._lookupBundleWithPath(normalizedPath);
								if (existingBundle == null) break block11;
								bundle = existingBundle;
								break block10;
							}
							existingBundle = NSBundle._lookupBundleWithPath(NSBundle._cleanNormalizedBundlePath(normalizedPath));
							if (existingBundle == null) break block12;
							bundle = existingBundle;
							break block10;
						}
						if (_primordialProperties == null) {
							_primordialProperties = NSBundle._userAndBundleProperties();
							_projectSearchPaths = NSValueUtilities.arrayValue(_primordialProperties.getProperty("NSProjectSearchPath"));
						}
						if (_projectSearchPaths == null) break block10;
						String projectBuildPath = _primordialProperties.getProperty("NSProjectBuildPath");
						bundleName = null;
						if (projectBuildPath == null) break block13;
						String normalizedBuildPath = NSPathUtilities.stringByNormalizingPath((String) projectBuildPath);
						String normalizedBundlePath = NSPathUtilities.stringByNormalizingPath((String) path);
						if (!normalizedBundlePath.startsWith(normalizedBuildPath)) break block14;
						NSArray<String> buildPathComponents = NSArray.componentsSeparatedByString((String) normalizedBuildPath, (String) File.separator);
						NSArray<String> bundlePathComponents = NSArray.componentsSeparatedByString((String) normalizedBundlePath, (String) File.separator);
						if (bundlePathComponents.count() > buildPathComponents.count()) {
							bundleName = (String) bundlePathComponents.objectAtIndex(buildPathComponents.count());
						}
						break block14;
					}
					NSArray<String> pathComponents = NSArray.componentsSeparatedByString((String) path, (String) File.separator);
					for (String pathComponent : pathComponents) {
						if (!pathComponent.endsWith(".framework") && !pathComponent.endsWith(".woa")) continue;
						bundleName = pathComponent.substring(0, pathComponent.lastIndexOf(46));
						break;
					}
				}
				if (bundleName != null) {
					for (String projectSearchPath : _projectSearchPaths) {
						File projectPath = new File(projectSearchPath, bundleName);
						if (!projectPath.exists()) continue;
						bundle = this._bundleForPath(projectPath.getAbsolutePath(), shouldCreateBundle, newIsJar);
						break;
					}
				}
			}
			if (bundle != null) {
				NSBundle existingBundle = NSBundle._bundleOrAppForName(bundle.name());
				if (existingBundle != null) {
					bundle = existingBundle;
				} else if (!shouldCreateBundle) {
					bundle = null;
				}
			}
			if (bundle instanceof NSProjectBundle) {
				((NSProjectBundle) bundle)._bundleLoadedFromPath(path);
			}
			return bundle;
		}

		/**
		 * Returns {@code true}/{@code false} if the project folder's actual on-disk layout
		 * unambiguously indicates Maven-standard or Fluffy Bunny layout, or {@code null} if
		 * neither physical marker is present (e.g. an empty/new project), in which case the
		 * caller should fall back to the {@code .project} nature id.
		 */
		private static Boolean mavenProjectFromPhysicalLayout(File projectFolder) {
			File resources = new File(projectFolder, "Resources");
			File components = new File(projectFolder, "Components");
			File webServerResources = new File(projectFolder, "WebServerResources");
			if (resources.isDirectory() && (components.isDirectory() || webServerResources.isDirectory())) {
				return Boolean.FALSE;
			}
			File srcMainJava = new File(projectFolder, "src/main/java");
			File srcMainResources = new File(projectFolder, "src/main/resources");
			File srcMainWoresources = new File(projectFolder, "src/main/woresources");
			if (srcMainJava.isDirectory() || srcMainResources.isDirectory() || srcMainWoresources.isDirectory()) {
				return Boolean.TRUE;
			}
			return null;
		}

		protected NSBundle createBundleFromProjectFolder(File projectFolder) {
			String folderName;
			boolean isBundle = false;
			Properties buildProperties = null;
			File buildPropertiesFile = new File(projectFolder, "woantbuild.properties");
			if (!buildPropertiesFile.exists()) {
				buildPropertiesFile = new File(projectFolder, "build.properties");
			}
			if (buildPropertiesFile.exists() && !(folderName = projectFolder.getName()).endsWith(".framework") && !folderName.endsWith(".woa")) {
				buildProperties = new Properties();
				try {
					FileInputStream buildPropertiesInputStream = new FileInputStream(buildPropertiesFile);
					try {
						buildProperties.load(buildPropertiesInputStream);
					} finally {
						((InputStream) buildPropertiesInputStream).close();
					}
					if (buildProperties.getProperty("framework.name") != null) {
						isBundle = true;
					} else {
						String projectType = buildProperties.getProperty("project.type");
						if ("framework".equals(projectType) || "application".equals(projectType)) {
							isBundle = true;
						} else if (projectType == null && buildProperties.getProperty("project.name") != null) {
							isBundle = true;
						} else if (projectType == null && buildProperties.getProperty("application.name") != null) {
							isBundle = true;
						}
					}
				} catch (Throwable t) {
					System.out.println("NSBundle: Can't read " + buildPropertiesFile + " (" + t + ")");
				}
			}
			Document eclipseProjectDocument = null;
			boolean mavenProjectFromNature = false;
			String projectPath = projectFolder.getPath();
			File eclipseProjectFile = new File(projectFolder, ".project");
			if (eclipseProjectFile.exists()) {
				try {
					eclipseProjectDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(eclipseProjectFile);
					eclipseProjectDocument.normalize();
					Element projectDescriptionElement = eclipseProjectDocument.getDocumentElement();
					Element naturesElement = (Element) projectDescriptionElement.getElementsByTagName("natures").item(0);
					NodeList naturesNodeList = naturesElement.getElementsByTagName("nature");
					int i = 0;
					while (i < naturesNodeList.getLength()) {
						Element natureElement = (Element) naturesNodeList.item(i);
						String nature = null;
						NodeList natureChildNodeList = natureElement.getChildNodes();
						int natureChildNodeNum = 0;
						while (natureChildNodeNum < natureChildNodeList.getLength()) {
							Node natureChildNode = natureChildNodeList.item(natureChildNodeNum);
							if (natureChildNode instanceof Text) {
								nature = natureChildNode.getNodeValue();
							}
							++natureChildNodeNum;
						}
						if (nature != null) {
							// Recognize both the modern m2e nature id (ERFoundation 1.2) and the
							// long-dead pre-2011 m2eclipse nature id (ERFoundation 1.0), so this
							// fallback behaves consistently regardless of ERFoundation version.
							if ("org.eclipse.m2e.core.maven2Nature".equals(nature) || "org.maven.ide.eclipse.maven2Nature".equals(nature)) {
								mavenProjectFromNature = true;
							} else if (nature.startsWith("org.objectstyle.wolips.")) {
								isBundle = true;
							}
						}
						++i;
					}
				} catch (Throwable t) {
					System.out.println("NSBundle: Can't read " + eclipseProjectFile + " (" + t + ")");
				}
			}
			// Prefer the project's actual on-disk layout over the (fragile, IDE-metadata-only)
			// .project nature id: a project can be m2e-managed for dependency resolution while
			// still physically using the classic Sources/Resources/Components/WebServerResources
			// ("Fluffy Bunny") layout, in which case it must NOT be treated as NSMavenProjectBundle
			// just because m2e added its nature. See the class-level javadoc for the full story.
			Boolean mavenProjectFromLayout = NSStandardProjectBundle.Factory.mavenProjectFromPhysicalLayout(projectFolder);
			boolean mavenProject = mavenProjectFromLayout != null ? mavenProjectFromLayout.booleanValue() : mavenProjectFromNature;
			NSStandardProjectBundle bundle = null;
			if (isBundle) {
				bundle = mavenProject ? new NSMavenProjectBundle(projectPath, buildProperties, eclipseProjectDocument) : new NSFluffyBunnyProjectBundle(projectPath, buildProperties, eclipseProjectDocument);
			}
			return bundle;
		}

		protected NSBundle _bundleForPath(String path, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle = null;
			File projectFolder = new File(path);
			while (bundle == null && projectFolder != null) {
				bundle = this.createBundleFromProjectFolder(projectFolder);
				projectFolder = projectFolder.getParentFile();
			}
			return bundle;
		}
	}
}
