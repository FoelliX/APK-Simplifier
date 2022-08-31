package de.foellix.aql.system.defaulttools.preprocessors.simplifier.decompiler;

import java.io.File;

import de.foellix.aql.Log;
import de.foellix.aql.helper.FileHelper;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

public class Decompiler {
	public static File decompile(FileToDecompile classFile, boolean appFile) {
		final JadxArgs jadxArgs = new JadxArgs();
		jadxArgs.setUseSourceNameAsClassAlias(true);
		jadxArgs.setInputFile(classFile.getFile());
		jadxArgs.setEscapeUnicode(true);
		jadxArgs.setRenamePrintable(true);
		jadxArgs.setRenameValid(true);
		jadxArgs.setDebugInfo(false);
		jadxArgs.setInlineAnonymousClasses(false);
		jadxArgs.setInlineMethods(false);
		jadxArgs.setReplaceConsts(false);
		final File outputDir = new File(FileHelper.getTempDirectory(),
				"jadx" + File.separator + (appFile ? "app" : "lib"));
		if (!outputDir.exists()) {
			outputDir.mkdir();
		}
		jadxArgs.setOutDir(outputDir);
		final File outputFile = new File(outputDir, "sources" + classFile.toString().replace(".class", ".java"));
		try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
			jadx.load();
			jadx.save();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		// Output
		if (outputFile.exists()) {
			Log.msg("Decompilation of \"" + classFile.getFile().getAbsolutePath() + "\" into \""
					+ outputFile.getAbsolutePath() + "\" successfull.", Log.DEBUG_DETAILED);
		} else {
			Log.warning("Decompilation of \"" + classFile.getFile().getAbsolutePath() + "\" into \""
					+ outputFile.getAbsolutePath() + "\" failed.");
		}
		return outputFile;
	}
}