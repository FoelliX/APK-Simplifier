package de.foellix.aql.system.defaulttools.preprocessors.simplifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.foellix.aql.Log;
import de.foellix.aql.helper.CLIHelper;
import de.foellix.aql.helper.FileHelper;
import de.foellix.aql.helper.HashHelper;
import de.foellix.aql.helper.Helper;
import de.foellix.aql.helper.ZipHelper;
import de.foellix.aql.system.ProcessWrapper;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.checker.EquivalenceChecker;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.decompiler.FileToDecompile;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.storage.SimilarityHash;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.storage.Storage;

// Note: Example download link https://dl.google.com/android/maven2/androidx/activity/activity/1.0.0/activity-1.0.0.aar
public class Comparator {
	private float threshold = 70f;

	public static void main(String[] args) {
		new Comparator().extract(args);
	}

	private void extract(String[] args) {
		if (args.length == 0) {
			Log.msg("Please provide at least an .apk file as input - e.g.:\njava -jar SimplifyParser.jar app.apk [output.txt]",
					Log.NORMAL);
		} else {
			this.threshold = Float.parseFloat(Config.getInstance().getProperty(Config.THRESHOLD));

			File output;
			if (args.length >= 2) {
				output = new File(args[1]);
				if (args.length == 3) {
					Log.setLogLevel(CLIHelper.evaluateLogLevel(args[2]));
				}
			} else {
				output = new File("output.txt");
			}

			final File apk = new File(args[0]);
			final String apkHash = HashHelper.sha256Hash(apk);
			final File dir = new File(FileHelper.getTempDirectory(), apkHash + "_lib");

			// Download and unzip libraries
			Log.msg("Step 1: Downloading and unzipping libraries", Log.NORMAL);
			final File dl = new File(dir, "downloads");
			final File outputClassesDir = new File(dl, "classes");
			outputClassesDir.mkdirs();
			dl.mkdirs();
			ZipHelper.unzip(apk, dir, true, ".*META-INF.*\\.version", true);
			final File meta = new File(dir, "META-INF");
			if (meta.exists()) {
				for (final File versionFile : meta.listFiles()) {
					final String version = Helper.replaceAllWhiteSpaceChars(getVersion(versionFile)).replace(" ", "");
					final String path = getPath(versionFile);
					final String artifact = getArtifact(versionFile);
					final String file = artifact + "-" + version + ".aar";
					final String url = "https://dl.google.com/android/maven2/" + path + "/" + artifact + "/" + version
							+ "/" + file;
					final File download = new File(dl, file);
					if (!download.exists()) {
						FileHelper.downloadFile(url, download);

						final File classesDir = new File(
								download.getAbsolutePath().substring(0, download.getAbsolutePath().length() - 4));
						ZipHelper.unzip(download, classesDir, true, "classes\\.jar", false);
						final File classes = new File(classesDir, "classes.jar");
						ZipHelper.unzip(classes, outputClassesDir);
					}
				}
			} else {
				Log.error("\"" + apk.getAbsolutePath()
						+ "\" does not contain library version information. Cannot simplify. Cancel!");
				Log.msg("Creating empty answer output: " + output.getAbsolutePath(), Log.DEBUG_DETAILED);
				try {
					output.createNewFile();
				} catch (final IOException e) {
					Log.error("Could not create empty output file: " + output.getAbsolutePath()
							+ Log.getExceptionAppendix(e));
				}
				return;
			}

			// Extract libraries from .apk
			Log.msg("Step 2: Extracting libraries from APK", Log.NORMAL);
			final File tempJar = new File(FileHelper.getTempDirectory(), apkHash + ".jar");
			if (!tempJar.exists()) {
				final File dex2jar = new File(Config.getInstance().getProperty(Config.DEX2JAR_PATH));
				final String[] cmd = new String[] { dex2jar.getAbsolutePath(), apk.getAbsolutePath(), "-o",
						tempJar.getAbsolutePath() };
				final ProcessBuilder pb = new ProcessBuilder(cmd);
				try {
					final ProcessWrapper pw = new ProcessWrapper(pb.start());
					if (pw.waitFor() != 0) {
						Log.error("Failed to execute dex2jar for: " + apk.getAbsolutePath());
					} else {
						Log.msg("Successfully executed dex2jar for: " + apk.getAbsolutePath(), Log.DEBUG);
					}
				} catch (final IOException e) {
					Log.error("Failed to execute dex2jar for: " + apk.getAbsolutePath() + Log.getExceptionAppendix(e));
				}
			}
			final File tempDir = new File(tempJar.getParentFile(),
					tempJar.getName().substring(0, tempJar.getName().length() - 4) + "_app");
			if (!tempDir.exists()) {
				tempDir.mkdir();
				ZipHelper.unzip(tempJar, tempDir);
			}

			// Extract libraries from .apk
			Log.msg("Step 3: Comparing", Log.NORMAL);
			final List<String> lines = compare(tempDir, outputClassesDir);
			try {
				Files.write(output.toPath(), lines);
			} catch (final IOException e) {
				Log.error("Could not write output file: " + output.getAbsolutePath() + Log.getExceptionAppendix(e));
			}

			// Store data
			Log.msg("Step 4: Saving result", Log.NORMAL);
			Storage.getInstance().saveData();
		}
	}

	private List<String> compare(File appDir, File libDir) {
		final List<String> excludes = new ArrayList<>();
		int keepCounter = 0;

		final EquivalenceChecker eq = new EquivalenceChecker();
		final Map<File, File> mappedFiles = new HashMap<>();
		final int noMatchCounter = findFiles(mappedFiles, appDir, libDir);
		for (final File classFile1 : mappedFiles.keySet()) {
			final FileToDecompile appClassFile = new FileToDecompile(classFile1, appDir);
			final FileToDecompile libClassFile = new FileToDecompile(mappedFiles.get(classFile1), libDir);
			final String className = appClassFile.toString().substring(1, appClassFile.toString().length() - 6)
					.replace("\\", ".").replace("/", ".");
			float result = -3;
			final SimilarityHash similarityHash = new SimilarityHash(HashHelper.sha256Hash(appClassFile.getFile()),
					result);
			if (Storage.getInstance().getData().getExcludedClasses().get(className) == null
					|| !Storage.getInstance().getData().getExcludedClasses().get(className).contains(similarityHash)) {
				result = eq.check(appClassFile, libClassFile, true);
				similarityHash.setSimilarity(result);
				Storage.getInstance().getData().add(className, similarityHash);
			} else if (Storage.getInstance().getData().getExcludedClasses().get(className) != null
					&& Storage.getInstance().getData().getExcludedClasses().get(className).contains(similarityHash)) {
				for (final SimilarityHash temp : Storage.getInstance().getData().getExcludedClasses().get(className)) {
					if (temp.equals(similarityHash)) {
						result = temp.getSimilarity();
						break;
					}
				}
			}
			final boolean keep = result < this.threshold;
			if (!keep) {
				excludes.add(className);
			} else {
				excludes.add("# " + className);
				keepCounter++;
			}
			if (keep || Log.logIt(Log.DEBUG_DETAILED)) {
				Log.msg((result < 0 ? "n/a (could not be determined)" : Math.round(result * 100f) / 100f + "%")
						+ "\t->\t(" + (!keep ? "Can be excluded" : "Must be kept") + ") " + className, Log.DEBUG);
			}
		}
		Log.msg("Overall " + (noMatchCounter + keepCounter) + " of " + (noMatchCounter + mappedFiles.keySet().size())
				+ " ("
				+ Math.round(((float) (noMatchCounter + keepCounter) / (noMatchCounter + mappedFiles.keySet().size()))
						* 10000f) / 100f
				+ "%) classes must be kept:\n\t- " + noMatchCounter + " of "
				+ (noMatchCounter + mappedFiles.keySet().size()) + " ("
				+ Math.round(((float) noMatchCounter / (noMatchCounter + mappedFiles.keySet().size())) * 10000f) / 100f
				+ "%) classes could not be matched with a library class, and\n\t- " + keepCounter + " of "
				+ (noMatchCounter + mappedFiles.keySet().size()) + " ("
				+ Math.round(((float) keepCounter / (noMatchCounter + mappedFiles.keySet().size())) * 10000f) / 100f
				+ "%) matched classes must be kept because they did not score a similarity of " + this.threshold
				+ "% or higher.", Log.NORMAL);
		Collections.sort(excludes);
		return excludes;
	}

	private int findFiles(Map<File, File> mappedFiles, File appDir, File libDir) {
		int keepCounter = 0;
		for (final File classFile1 : appDir.listFiles()) {
			final File classFile2 = new File(libDir, classFile1.getName());
			if (classFile2.exists()) {
				if (classFile1.isDirectory()) {
					keepCounter += findFiles(mappedFiles, classFile1, classFile2);
				} else if (classFile1.getName().endsWith(".class")) {
					mappedFiles.put(classFile1, classFile2);
				} else {
					Log.msg("Unknown file type: " + classFile1.getAbsolutePath(), Log.DEBUG);
				}
			} else if (!classFile1.isDirectory()) {
				keepCounter++;
				Log.msg("No counterpart found for: " + classFile1.getAbsolutePath(), Log.DEBUG);
			} else {
				keepCounter += findFiles(mappedFiles, classFile1, classFile2);
			}
		}
		return keepCounter;
	}

	private String getVersion(File versionFile) {
		try {
			return Files.readAllLines(versionFile.toPath()).iterator().next();
		} catch (final Exception e) {
			Log.error("Could not read .version file: " + versionFile.getAbsolutePath() + Log.getExceptionAppendix(e));
			return "1.0.0";
		}
	}

	private String getPath(File versionFile) {
		return versionFile.getName().substring(0, versionFile.getName().indexOf("_")).replace(".", "/");
	}

	private String getArtifact(File versionFile) {
		String temp = versionFile.getName().substring(versionFile.getName().indexOf("_") + 1);
		temp = temp.substring(0, temp.lastIndexOf('.'));
		return temp;
	}
}