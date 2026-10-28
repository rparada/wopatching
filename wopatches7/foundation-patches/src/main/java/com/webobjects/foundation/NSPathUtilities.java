package com.webobjects.foundation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;

public class NSPathUtilities {
	public static final Class _CLASS = _NSUtilities
			._classWithFullySpecifiedName("com.webobjects.foundation.NSPathUtilities");

	public static String _fileURLPrefix = File.pathSeparatorChar == ';' ? "file:///" : "file://";

	private NSPathUtilities() {
		throw new IllegalStateException("Can't instantiate an instance of class " + getClass().getName());
	}

	public static String homeDirectory() {
		return System.getProperty("user.home");
	}

	public static String _fileSeparatorStandardizedPath(final String path) {
		if (path == null) {
			return "";
		}
		return File.separatorChar != '/' ? path.replace(File.separatorChar, '/') : path;
	}

	public static String _normalizedPath(final String path) {
		if (path == null) {
			return "";
		}
		return File.separatorChar != '/' ? path.replace('/', File.separatorChar) : path;
	}

	public static String _standardizedPath(final String path) {
		if (path != null) {
			String aPath = path;
			final int pathLength = aPath.length();
			if (pathLength > 0) {
				aPath = _fileSeparatorStandardizedPath(aPath);
				int index = aPath.indexOf("//");
				while (index >= 0) {
					if (index == pathLength - 1) {
						aPath = aPath.substring(0, index);
						break;
					}
					aPath = aPath.substring(0, index).concat(aPath.substring(index + 1));
					index = aPath.indexOf("//");
				}
				return aPath;
			}
		}
		return "";
	}

	public static String pathExtension(final String path) {
		if (path != null) {
			final int pathLength = path.length();
			if (pathLength > 0) {
				final String standardizedPath = _fileSeparatorStandardizedPath(path);
				final int extensionIndex = standardizedPath.lastIndexOf('.');
				if (extensionIndex >= 0) {
					int separatorIndex = standardizedPath.lastIndexOf('/');
					final int lastIndex = pathLength - 1;
					int substringIndex = pathLength;
					if (separatorIndex == lastIndex) {
						separatorIndex = standardizedPath.lastIndexOf('/', lastIndex - 1);
						substringIndex = lastIndex;
					}
					if ((separatorIndex < 0 || extensionIndex > separatorIndex) &&
							extensionIndex < pathLength - 1) {
						return standardizedPath.substring(extensionIndex + 1, substringIndex);
					}
				}
			}
		}
		return "";
	}

	public static String lastPathComponent(final String path) {
		String pathComponent = path;
		if (path == null) {
			return "";
		}
		final int pathLength = path.length();
		if (pathLength == 0) {
			return "";
		}
		final String standardizedPath = _fileSeparatorStandardizedPath(path);
		int separatorIndex = standardizedPath.lastIndexOf('/');
		final int lastIndex = pathLength - 1;
		if (separatorIndex == lastIndex) {
			separatorIndex = standardizedPath.lastIndexOf('/', lastIndex - 1);
			if (separatorIndex >= 0 && separatorIndex < lastIndex) {
				pathComponent = path.substring(separatorIndex + 1, lastIndex);
			} else {
				pathComponent = path.substring(0, lastIndex);
			}
		} else if (separatorIndex >= 0 && separatorIndex < lastIndex) {
			pathComponent = path.substring(separatorIndex + 1);
		}
		if (path.startsWith(pathComponent) && pathComponent.length() == 2 && Character.isLetter(pathComponent.charAt(0))
				&& pathComponent.endsWith(":") && File.separatorChar == '\\' && File.pathSeparatorChar == ';') {
			pathComponent = "";
		}
		return pathComponent;
	}

	public static String stringByDeletingLastPathComponent(final String path) {
		if (path != null) {
			final int pathLength = path.length();
			if (pathLength > 0) {
				final String standardizedPath = _fileSeparatorStandardizedPath(path);
				int separatorIndex = standardizedPath.lastIndexOf('/');
				int firstSeparatorIndex = 0;
				final int lastIndex = pathLength - 1;
				if (Character.isLetter(standardizedPath.charAt(0))) {
					if (standardizedPath.indexOf(":/") == 1 || standardizedPath.indexOf(":\\") == 1) {
						firstSeparatorIndex = 2;
						if (separatorIndex == -1) {
							separatorIndex = standardizedPath.lastIndexOf('\\');
						}
					} else if (standardizedPath.indexOf(":") == 1) {
						separatorIndex = firstSeparatorIndex = 1;
					}
				}
				if (separatorIndex > -1 && separatorIndex == firstSeparatorIndex) {
					return path.substring(0, firstSeparatorIndex + 1);
				}
				if (separatorIndex == lastIndex) {
					separatorIndex = standardizedPath.lastIndexOf('/', lastIndex - 1);
				}
				if (separatorIndex > -1 && separatorIndex == firstSeparatorIndex) {
					return path.substring(0, firstSeparatorIndex + 1);
				}
				if (separatorIndex > 0) {
					return path.substring(0, separatorIndex);
				}
			}
		}
		return "";
	}

	public static String stringByDeletingPathExtension(final String path) {
		String result;
		final String standardizedPath = _fileSeparatorStandardizedPath(path);
		final int length = standardizedPath.length();
		int pos = length - 1;
		while (pos >= 0 && standardizedPath.charAt(pos) == '/') {
			pos--;
		}
		if (pos == -1) {
			if (length == 0) {
				return "";
			}
			return File.separator;
		}
		final int lastSlash = pos;
		while (pos >= 0 && standardizedPath.charAt(pos) != '.') {
			pos--;
		}
		if (pos == -1) {
			result = path.substring(0, lastSlash + 1);
		} else {
			result = path.substring(0, pos);
		}
		return result;
	}

	public static String stringByAppendingPathComponent(final String path, final String component) {
		if (path == null) {
			return component == null ? "" : component;
		}
		if (component == null) {
			return path;
		}
		final int pathLength = path.length();
		final int componentLength = component.length();
		if (pathLength == 0) {
			return component;
		}
		if (componentLength == 0) {
			return path;
		}
		final boolean pathEndsWithFileSeparator = path.endsWith(File.separator);
		final boolean componentStartsWithFileSeparator = component.startsWith(File.separator);
		if (pathEndsWithFileSeparator && componentStartsWithFileSeparator) {
			final StringBuffer stringBuffer = new StringBuffer(pathLength + componentLength - 1);
			stringBuffer.append(path.substring(0, pathLength - 1));
			stringBuffer.append(component);
			return new String(stringBuffer);
		}
		if (pathEndsWithFileSeparator || componentStartsWithFileSeparator) {
			final StringBuffer stringBuffer = new StringBuffer(pathLength + componentLength);
			stringBuffer.append(path);
			stringBuffer.append(component);
			return new String(stringBuffer);
		}
		final StringBuffer buffer = new StringBuffer(pathLength + componentLength + 1);
		buffer.append(path);
		buffer.append(File.separator);
		buffer.append(component);
		return new String(buffer);
	}

	public static String stringByAppendingPathExtension(final String path, final String extension) {
		if (path == null) {
			if (extension != null) {
				final StringBuffer stringBuffer = new StringBuffer(extension.length() + 1);
				stringBuffer.append('.');
				stringBuffer.append(extension);
				return new String(stringBuffer);
			}
			return "";
		}
		if (extension == null) {
			return path;
		}
		final int pathLength = path.length();
		if (path.endsWith("/") && pathLength > 1) {
			final StringBuffer stringBuffer = new StringBuffer(pathLength + extension.length());
			stringBuffer.append(path.substring(0, pathLength - 1));
			stringBuffer.append('.');
			stringBuffer.append(extension);
			return new String(stringBuffer);
		}
		final StringBuffer buffer = new StringBuffer(pathLength + extension.length() + 1);
		buffer.append(path);
		buffer.append('.');
		buffer.append(extension);
		return new String(buffer);
	}

	public static String stringByNormalizingExistingPath(final String path) {
		if (path != null) {
			final File f = new File(path);
			if (f.exists()) {
				try {
					return f.getCanonicalPath();
				} catch (final IOException e) {
					if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
						NSLog.debug.appendln("Exception while getting canonical path: " + path);
						NSLog.debug.appendln(e);
					}
				}
			}
		}
		return "";
	}

	@Deprecated
	public static String stringByStandardizingPath(final String path) {
		return _stringByStandardizingPath(path);
	}

	public static String _stringByStandardizingPath(final String path) {
		if (path == null) {
			return "";
		}
		final boolean destandardizePath = File.separatorChar != '/' ? path.indexOf(File.separatorChar) >= 0 : false;
		String aPath = _standardizedPath(path);
		int pathLength = aPath.length();
		if (aPath.startsWith("~")) {
			final String homeDirectory = homeDirectory();
			aPath = pathLength == 1 ? homeDirectory : homeDirectory + aPath.substring(1);
		}
		if (aPath.endsWith("/") && pathLength > 1) {
			aPath = aPath.substring(0, --pathLength);
		}
		int searchStartIndex = 0;
		int index1 = aPath.indexOf("..", searchStartIndex);
		while (index1 >= 0) {
			if (index1 == 0) {
				throw new IllegalArgumentException("<NSPathUtilities> Unable to resolve path starting with ..");
			}
			if (aPath.charAt(index1 - 1) == '/') {
				final int index2 = aPath.lastIndexOf('/', index1 - 2);
				if (index1 + 2 >= pathLength) {
					if (index2 < 0) {
						aPath = "";
					} else if (index2 == 0) {
						aPath = "/";
					} else {
						aPath = aPath.substring(0, index2);
					}
				} else if (aPath.charAt(index1 + 2) == '/') {
					if (index2 < 0) {
						aPath = aPath.substring(index1 + 3);
					} else if (index2 == 0) {
						aPath = aPath.substring(index1 + 2);
					} else {
						aPath = aPath.substring(0, index2 + 1) + aPath.substring(index1 + 3);
					}
				} else {
					searchStartIndex = index1 + 2;
				}
			} else {
				searchStartIndex = index1 + 2;
			}
			index1 = aPath.indexOf("..", searchStartIndex);
			pathLength = aPath.length();
		}
		if (destandardizePath) {
			aPath = aPath.replace('/', File.separatorChar);
		}
		return aPath;
	}

	@Deprecated
	public static boolean pathIsEqualToString(final String path1, final String path2) {
		if (path1 == path2) {
			return true;
		}
		if (path1 == null || path2 == null) {
			return false;
		}
		return new File(path1).equals(new File(path2));
	}

	@Deprecated
	public static boolean pathIsAbsolute(final String path) {
		return path != null ? new File(path).isAbsolute() : false;
	}

	@Deprecated
	public static boolean fileExistsAtPath(final String path) {
		return path != null ? new File(path).exists() : false;
	}

	/**
	 * {@link Paths#get(URI)} requires the {@link java.nio.file.FileSystem} for the URI's scheme
	 * to already be open/mounted in this JVM. For {@code file:} URLs the default filesystem is
	 * always available, but for {@code jar:} URLs (e.g. a resource such as an {@code .eomodeld}
	 * packaged inside a framework jar, rather than an exploded {@code target/classes} directory)
	 * nothing may have opened that specific jar as a zip filesystem yet, in which case
	 * {@code Paths.get(uri)} throws an uncaught {@link FileSystemNotFoundException}. Open the
	 * filesystem on demand the first time a given jar/jrt URI is encountered, then retry, instead
	 * of letting callers of {@link #fileExistsAtPathURL(URL)}, {@link #_contentLengthForPathURL(URL)},
	 * and {@link #_isDirectoryAtPathURL(URL)} crash the first time this happens.
	 */
	private static Path _pathForUri(final URI uri) throws IOException {
		try {
			return Paths.get(uri);
		}
		catch (final FileSystemNotFoundException fileSystemNotFoundException) {
			try {
				FileSystems.newFileSystem(uri, Collections.emptyMap());
			}
			catch (final FileSystemAlreadyExistsException fileSystemAlreadyExistsException) {
				// Another thread mounted it first between our failed Paths.get(uri) and here; fine.
			}
			return Paths.get(uri);
		}
	}

	public static boolean fileExistsAtPathURL(final URL url) {
		boolean result = false;
		if (url != null) {
			try {
				final Path p = _pathForUri(url.toURI());
				result = Files.exists(p);
			} catch (final URISyntaxException | IOException | UncheckedIOException e) {
				NSLog.debug.appendln("Error reading exists for url: " + url);
			}
		}
		return result;
	}

	public static boolean _isFileProtocol(final URL url) {
		return url != null && "file".equals(url.getProtocol());
	}

	public static boolean _isJarProtocol(final URL url) {
		return url != null && "jar".equals(url.getProtocol());
	}

	public static long _contentLengthForPathURL(final URL url) {
		long contentLength = -1L;
		if (url != null) {
			try {
				final Path p = _pathForUri(url.toURI());
				contentLength = Files.size(p);
			} catch (URISyntaxException | IOException | UncheckedIOException e) {
				NSLog.debug.appendln("Error reading content length for url: " + url);
			}
		}
		return contentLength;
	}

	public static long _lastModifiedForPathURL(final URL url) {
		long lastModified = 0L;
		if (url != null &&
				_isFileProtocol(url)) {
			final File file = new File(url.getPath());
			lastModified = file.lastModified();
		}
		return lastModified;
	}

	@Deprecated
	public static URL URLWithPath(final String path) {
		return _URLWithPath(path);
	}

	public static URL _URLWithPath(final String path) {
		URL url = null;
		if (path == null) {
			return null;
		}
		try {
			url = new URL(_fileURLPrefix.concat(stringByNormalizingPath(path)));
		} catch (final MalformedURLException e) {
		}
		return url;
	}

	public static String stringByNormalizingPath(final String path) {
		if (path != null) {
			final File f = new File(path);
			try {
				return f.getCanonicalPath();
			} catch (final IOException e) {
				if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
					NSLog.debug.appendln("Exception while getting canonical path: " + path);
					NSLog.debug.appendln(e);
				}
			}
		}
		return "";
	}

	public static URL _URLWithPathURL(final String path) {
		URL url = null;
		if (path == null) {
			return null;
		}
		try {
			url = new URL(path);
		} catch (final MalformedURLException e) {
		}
		if (url == null) {
			url = _URLWithPath(path);
		}
		return url;
	}

	public static URL _URLWithFile(final File aFile) {
		URL url = null;
		if (aFile != null) {
			try {
				url = new URL(aFile.getCanonicalPath());
			} catch (final Exception e) {
			}
		}
		return url;
	}

	public static File _FileWithURL(final URL url) {
		File file = null;
		if (_isFileProtocol(url)) {
			file = new File(url.getPath());
		}
		return file;
	}

	public static NSArray<String> _directoryContentsAtPath(final String path) {
		if (path == null) {
			return NSArray.emptyArray();
		}
		return new NSArray<>(new File(path).list());
	}

	public static boolean _isDirectory(final String path) {
		return path != null ? new File(path).isDirectory() : false;
	}

	public static boolean _isDirectoryAtPathURL(final URL url) {
		boolean result = false;
		if (url != null) {
			try {
				final Path p = _pathForUri(url.toURI());
				result = Files.exists(p) && Files.isDirectory(p);
			} catch (final URISyntaxException | IOException | UncheckedIOException e) {
				NSLog.debug.appendln("Error reading isDirectory for url: " + url);
			}
		}
		return result;
	}

	public static void _removeFileAtPath(final String path) {
		if (path == null) {
			return;
		}
		if (fileExistsAtPath(path) && _isDirectory(path)) {
			final NSArray<String> content = _directoryContentsAtPath(path);
			Enumeration<String> enumerator = null;
			if (content != null) {
				enumerator = content.objectEnumerator();
				while (enumerator.hasMoreElements()) {
					_removeFileAtPath(path + File.separator + enumerator.nextElement());
				}
			}
		}
		final File file = new File(path);
		file.delete();
	}

	public static void _movePath(final String src, final String dest) {
		if (src == null || dest == null) {
			return;
		}
		final File fileDest = new File(dest);
		final File fileSrc = new File(src);
		fileSrc.renameTo(fileDest);
	}

	public static boolean _overwriteFileWithFile(final File originalFile, final File newFile) {
		final File parent = originalFile.getParentFile();
		if (parent != null) {
			File backup = null;
			try {
				if (originalFile.exists()) {
					backup = File.createTempFile("backup", "tmp", parent);
					backup.delete();
				}
			} catch (final IOException ioe) {
				NSLog.err.appendln("Failed to create backup file in directory " + parent);
				return false;
			}
			final String originalPath = originalFile.getAbsolutePath();
			if (backup != null && !originalFile.renameTo(backup)) {
				NSLog.err.appendln("Failed to rename " + originalFile + " to " + backup);
				return false;
			}
			if (newFile.renameTo(new File(originalPath))) {
				if (backup != null) {
					backup.delete();
				}
				return true;
			}
			if (backup != null && !originalFile.renameTo(new File(originalPath))) {
				throw new IllegalStateException("Tried to move " + newFile + " on to " + originalFile
						+ " but failed. Attempts at restoring the original conditions have failed. The original file is at "
						+ backup);
			}
		}
		return false;
	}

	public static boolean _createDirectory(final String path) {
		if (path == null) {
			return false;
		}
		return new File(path).mkdirs();
	}

	public static String _currentDirectoryPath() {
		return System.getProperty("user.dir");
	}

	@Deprecated
	public static boolean _fileAtPathIsWritable(final String path) {
		return path != null ? new File(path).canWrite() : false;
	}

	public static boolean _fileAtPathURLIsWritable(final URL url) {
		if (url == null) {
			return false;
		}
		boolean result = false;
		if (!_isJarProtocol(url) && _isFileProtocol(url)) {
			try {
				result = new File(url.getPath()).canWrite();
			} catch (final Exception exception) {
			}
		}
		return result;
	}

	public static void _copyPath(final String source, final String dest, final Object sender) {
		if (source.equals(dest)) {
			return;
		}
		try (final FileInputStream in = new FileInputStream(source);
				final FileOutputStream out = new FileOutputStream(dest);) {
			final byte[] buffer = new byte[in.available()];
			int chunk = in.read(buffer);
			while (chunk >= 0) {
				out.write(buffer, 0, chunk);
				chunk = in.read(buffer);
			}
		} catch (final IOException e) {
			if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
				NSLog.debug.appendln("Exception while copying path " + source + " to path " + dest);
				NSLog.debug.appendln(e);
			}
		}
	}
}