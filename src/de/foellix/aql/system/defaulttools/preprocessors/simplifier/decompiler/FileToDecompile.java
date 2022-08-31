package de.foellix.aql.system.defaulttools.preprocessors.simplifier.decompiler;

import java.io.File;

public class FileToDecompile {
	private File file;
	private File srcDir;

	public FileToDecompile(File file, File srcDir) {
		this.file = file;
		this.srcDir = srcDir;
	}

	public File getFile() {
		return this.file;
	}

	public File getSrcDir() {
		return this.srcDir;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setSrcDir(File srcDir) {
		this.srcDir = srcDir;
	}

	@Override
	public String toString() {
		return this.file.getAbsolutePath().replace(this.srcDir.getAbsolutePath(), "");
	}
}