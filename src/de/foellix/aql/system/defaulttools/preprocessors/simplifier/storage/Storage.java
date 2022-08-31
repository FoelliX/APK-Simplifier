package de.foellix.aql.system.defaulttools.preprocessors.simplifier.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedList;

import de.foellix.aql.Log;

public class Storage {
	private static final File DATA_FILE = new File("data/SimplifiableList.txt");

	private Data data;

	private static Storage instance = new Storage();

	private Storage() {
		loadData();
	}

	public static Storage getInstance() {
		return instance;
	}

	public Data getData() {
		return this.data;
	}

	private void loadData() {
		if (DATA_FILE.exists()) {
			try {
				this.data = new Data(Files.readAllLines(DATA_FILE.toPath()));
			} catch (final IOException e) {
				Log.warning("List of classes to simplify has not been found. Creating a new one: "
						+ DATA_FILE.getAbsolutePath() + Log.getExceptionAppendix(e));
			}
		} else {
			this.data = new Data();
		}
	}

	public void saveData() {
		if ((DATA_FILE.getParentFile().exists() && DATA_FILE.getParentFile().isDirectory())
				|| DATA_FILE.getParentFile().mkdirs()) {
			final StringBuilder sb = new StringBuilder();
			final LinkedList<String> sortedEntries = new LinkedList<>(this.data.getExcludedClasses().keySet());
			Collections.sort(sortedEntries);
			for (final String name : sortedEntries) {
				sb.append(name + ": " + this.data.getExcludedClasses().get(name) + "\n");
			}
			try {
				if (!DATA_FILE.exists() || DATA_FILE.delete()) {
					Files.write(DATA_FILE.toPath(), sb.toString().getBytes());
				} else {
					Log.warning("Could not update classes to simplify in \"" + DATA_FILE.getAbsolutePath() + "\".");
				}
			} catch (final IOException e) {
				Log.warning("Could not update classes to simplify in \"" + DATA_FILE.getAbsolutePath() + "\"."
						+ Log.getExceptionAppendix(e));
			}
		} else {
			Log.warning("Could not find or create directory (" + DATA_FILE.getParentFile().getAbsolutePath() + ").");
		}
	}
}