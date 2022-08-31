package de.foellix.aql.system.defaulttools.preprocessors.simplifier.checker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithCondition;
import com.github.javaparser.ast.stmt.BlockStmt;

import de.foellix.aql.Log;
import de.foellix.aql.helper.FileHelper;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.decompiler.Decompiler;
import de.foellix.aql.system.defaulttools.preprocessors.simplifier.decompiler.FileToDecompile;
import de.jplag.JPlag;
import de.jplag.JPlagComparison;
import de.jplag.JPlagResult;
import de.jplag.exceptions.ExitException;
import de.jplag.options.JPlagOptions;
import de.jplag.options.LanguageOption;

public class EquivalenceChecker {
	private int varCounter;

	public float check(FileToDecompile classOrJavaFile1, FileToDecompile classOrJavaFile2) {
		return check(classOrJavaFile1, classOrJavaFile2, false);
	}

	public float check(FileToDecompile classOrJavaFile1, FileToDecompile classOrJavaFile2, boolean classFiles) {
		File javaFile1;
		File javaFile2;
		if (classFiles) {
			javaFile1 = Decompiler.decompile(classOrJavaFile1, true);
			javaFile2 = Decompiler.decompile(classOrJavaFile2, false);
		} else {
			javaFile1 = classOrJavaFile1.getFile();
			javaFile2 = classOrJavaFile2.getFile();
		}

		if (!javaFile1.exists() || !javaFile2.exists()) {
			if (!javaFile1.exists()) {
				Log.error("Could not decompile file: " + classOrJavaFile1.getFile().getAbsolutePath());
			}
			if (!javaFile2.exists()) {
				Log.error("Could not decompile file: " + classOrJavaFile2.getFile().getAbsolutePath());
			}
			return 0;
		}

		this.varCounter = 0;

		try {
			final String parsedContent1 = parse(javaFile1);
			final String parsedContent2 = parse(javaFile2);

			if (parsedContent1 == null || parsedContent2 == null) {
				throw new NullPointerException("Parsing error!");
			}

			final File javaSimpleFile1 = new File(javaFile1.getAbsolutePath().replace(".java", "_simple.java"));
			final File javaSimpleFile2 = new File(javaFile2.getAbsolutePath().replace(".java", "_simple.java"));
			try {
				Files.write(javaSimpleFile1.toPath(), parsedContent1.getBytes());
				Files.write(javaSimpleFile2.toPath(), parsedContent2.getBytes());
			} catch (final IOException e) {
				Log.error("Could not write simplified .java files!" + Log.getExceptionAppendix(e));
			}

			return checkWithJPlag(javaSimpleFile1, javaSimpleFile2);
		} catch (final Exception e) {
			Log.error("Comparison not possible!");
			return -2f;
		}
	}

	private float checkWithJPlag(File javaFile1, File javaFile2) {
		// Copy files to same/temp directory
		final File temp = new File(FileHelper.getTempDirectory(), "jplag");
		try {
			// Create temp directory
			if (temp.exists()) {
				if (temp.isDirectory()) {
					FileHelper.deleteDir(temp);
				} else {
					temp.delete();
				}
			}
			temp.mkdir();
			new File(temp, "app").mkdir();
			new File(temp, "lib").mkdir();

			// Copy files
			Files.copy(javaFile1.toPath(), new File(temp, "app" + File.separator + javaFile1.getName()).toPath());
			Files.copy(javaFile2.toPath(), new File(temp, "lib" + File.separator + javaFile2.getName()).toPath());
		} catch (final Exception e) {
			Log.error("Could not copy files for JPlag comparison!" + Log.getExceptionAppendix(e));
		}

		// Compare
		try {
			final JPlagOptions options = new JPlagOptions(temp.getAbsolutePath(), LanguageOption.JAVA);
			options.setMinimumTokenMatch(0);

			final JPlag jplag = new JPlag(options);
			final JPlagResult result = jplag.run();

			for (final JPlagComparison comparison : result.getComparisons()) {
				final float similarity = comparison.similarity();
				return similarity;
			}
		} catch (final ExitException e) {
			Log.error("JPlag comparison failed!" + Log.getExceptionAppendix(e));
		}
		return -1f;
	}

	private String parse(File javaFile) {
		try {
			final String decompiledContent = preprocessDecompiledFile(javaFile);
			final CompilationUnit content = StaticJavaParser.parse(decompiledContent);
			for (final TypeDeclaration<?> type : content.getTypes()) {
				if (type instanceof ClassOrInterfaceDeclaration) {
					final ClassOrInterfaceDeclaration c = (ClassOrInterfaceDeclaration) type;
					for (final MethodDeclaration m : c.getMethods()) {
						parseChildren(m.getChildNodes());
					}
				}
			}
			return content.toString();
		} catch (final Exception e) {
			Log.error("Could not parse .java file: " + javaFile.getAbsolutePath());
		}
		return null;
	}

	private void parseChildren(List<Node> childNodes) {
		final List<Node> removes = new LinkedList<>();
		parseChildren(childNodes, removes);
		for (final Node child : removes) {
			child.remove();
		}
	}

	private void parseChildren(List<Node> childNodes, List<Node> removes) {
		parseChildren(childNodes, removes, 1);
	}

	private void parseChildren(List<Node> childNodes, List<Node> removes, int level) {
		for (final Node child : childNodes) {
			if (child instanceof MarkerAnnotationExpr) {
				removes.add(child);
			} else if (child instanceof Parameter) {
				final Parameter p = (Parameter) child;
				p.setName(new SimpleName("v" + ++this.varCounter));
			} else if (child instanceof NameExpr) {
				final NameExpr n = (NameExpr) child;
				n.setName(new SimpleName("v" + ++this.varCounter));
			}
			if (child instanceof NodeWithCondition) {
				parseChildren(child.getChildNodes(), removes);
			} else if (child instanceof BlockStmt) {
				parseChildren(child.getChildNodes(), removes);

			} else if (!child.getChildNodes().isEmpty()) {
				parseChildren(child.getChildNodes(), removes, level + 1);
			}
		}
	}

	private String preprocessDecompiledFile(File classFile1) throws IOException {
		String content = new String(Files.readAllBytes(classFile1.toPath()));

		// Replace inner classes (constructors)
		content = Pattern.compile("new ([0-9]+)\\(").matcher(content).replaceAll(new Function<MatchResult, String>() {
			@Override
			public String apply(MatchResult result) {
				final String replacement = result.group().replace(result.group(1), "_" + result.group(1));
				Log.msg("(1) Replacing \"" + result.group() + "\" by \"" + replacement + "\".", Log.DEBUG_DETAILED);
				return replacement;
			}
		});

		// Replace inner classes (uses)
		content = Pattern.compile("\\.([0-9]+)[ |\\)]").matcher(content)
				.replaceAll(new Function<MatchResult, String>() {
					@Override
					public String apply(MatchResult result) {
						final String replacement = result.group().replace("." + result.group(1),
								"\\$" + result.group(1));
						Log.msg("(2) Replacing \"" + result.group() + "\" by \"" + replacement + "\".",
								Log.DEBUG_DETAILED);
						return replacement;
					}
				});

		// Replace inner classes (fields)
		content = Pattern.compile("\\n\\s*([0-9]+) ").matcher(content).replaceAll(new Function<MatchResult, String>() {
			@Override
			public String apply(MatchResult result) {
				final String replacement = result.group().replace(result.group(1), "_" + result.group(1));
				Log.msg("(3) Replacing \"" + result.group() + "\" by \"" + replacement + "\".", Log.DEBUG_DETAILED);
				return replacement;
			}
		});

		// Replace protected classes
		content = Pattern.compile("protected(\\s)+(final\\s+)?class").matcher(content)
				.replaceAll(new Function<MatchResult, String>() {
					@Override
					public String apply(MatchResult result) {
						final String replacement = "public class";
						Log.msg("(4) Replacing \"" + result.group() + "\" by \"" + replacement + "\".",
								Log.DEBUG_DETAILED);
						return replacement;
					}
				});

		// Remove comments
		content = content.replaceAll("/\\*[^\\*]+\\*/", "");

		return content;
	}
}