/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.gradle.tasks.bundling;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.testsupport.classpath.ClassPathExclusions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BootWar}.
 *
 * @author Andy Wilkinson
 * @author Scott Frederick
 */
@ClassPathExclusions("kotlin-daemon-client-*")
class BootWarTests extends AbstractBootArchiveTests<BootWar> {

	BootWarTests() {
		super(BootWar.class, "org.springframework.boot.loader.launch.WarLauncher", "WEB-INF/lib/", "WEB-INF/classes/",
				"WEB-INF/");
	}

	@BeforeEach
	void setUp() {
		this.getTask().getTargetJavaVersion().set(JavaVersion.VERSION_17);
	}

	@Test
	void providedClasspathJarsArePackagedInWebInfLibProvided() throws IOException {
		getTask().getMainClass().set("com.example.Main");
		getTask().providedClasspath(jarFile("one.jar"), jarFile("two.jar"));
		executeTask();
		try (JarFile jarFile = new JarFile(getTask().getArchiveFile().get().getAsFile())) {
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/one.jar")).isNotNull();
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/two.jar")).isNotNull();
		}
	}

	@Test
	void providedClasspathCanBeSetUsingAFileCollection() throws IOException {
		getTask().getMainClass().set("com.example.Main");
		getTask().providedClasspath(jarFile("one.jar"));
		getTask().setProvidedClasspath(getTask().getProject().files(jarFile("two.jar")));
		executeTask();
		try (JarFile jarFile = new JarFile(getTask().getArchiveFile().get().getAsFile())) {
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/one.jar")).isNull();
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/two.jar")).isNotNull();
		}
	}

	@Test
	void providedClasspathCanBeSetUsingAnObject() throws IOException {
		getTask().getMainClass().set("com.example.Main");
		getTask().providedClasspath(jarFile("one.jar"));
		getTask().setProvidedClasspath(jarFile("two.jar"));
		executeTask();
		try (JarFile jarFile = new JarFile(getTask().getArchiveFile().get().getAsFile())) {
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/one.jar")).isNull();
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/two.jar")).isNotNull();
		}
	}

	@Test
	void devtoolsJarIsExcludedByDefaultWhenItsOnTheProvidedClasspath() throws IOException {
		getTask().getMainClass().set("com.example.Main");
		getTask().providedClasspath(newFile("spring-boot-devtools-0.1.2.jar"));
		executeTask();
		try (JarFile jarFile = new JarFile(getTask().getArchiveFile().get().getAsFile())) {
			assertThat(jarFile.getEntry("WEB-INF/lib-provided/spring-boot-devtools-0.1.2.jar")).isNull();
		}
	}

	@Test
	void webappResourcesInDirectoriesThatOverlapWithLoaderCanBePackaged() throws IOException {
		File webappDirectory = new File(this.temp, "src/main/webapp");
		webappDirectory.mkdirs();
		File orgDirectory = new File(webappDirectory, "org");
		orgDirectory.mkdir();
		new File(orgDirectory, "foo.txt").createNewFile();
		getTask().from(webappDirectory);
		getTask().getMainClass().set("com.example.Main");
		executeTask();
		try (JarFile jarFile = new JarFile(getTask().getArchiveFile().get().getAsFile())) {
			assertThat(jarFile.getEntry("org/")).isNotNull();
			assertThat(jarFile.getEntry("org/foo.txt")).isNotNull();
		}
	}

	@Test
	void libProvidedEntriesAreWrittenAfterLibEntries() throws IOException {
		getTask().getMainClass().set("com.example.Main");
		getTask().classpath(jarFile("library.jar"));
		getTask().providedClasspath(jarFile("provided-library.jar"));
		executeTask();
		assertThat(getEntryNames(getTask().getArchiveFile().get().getAsFile()))
			.containsSubsequence("WEB-INF/lib/library.jar", "WEB-INF/lib-provided/provided-library.jar");
	}

	@Test
	void whenWarIsLayeredClasspathIndexPointsToLayeredLibs() throws IOException {
		try (JarFile jarFile = new JarFile(createLayeredJar())) {
			assertThat(entryLines(jarFile, "WEB-INF/classpath.idx")).containsExactly(
					"- \"WEB-INF/lib/first-library.jar\"", "- \"WEB-INF/lib/second-library.jar\"",
					"- \"WEB-INF/lib/third-library-SNAPSHOT.jar\"", "- \"WEB-INF/lib/fourth-library.jar\"",
					"- \"WEB-INF/lib/first-project-library.jar\"",
					"- \"WEB-INF/lib/second-project-library-SNAPSHOT.jar\"");
		}
	}

	@Test
	void classpathIndexPointsToWebInfLibs() throws IOException {
		try (JarFile jarFile = new JarFile(createPopulatedJar())) {
			assertThat(jarFile.getManifest().getMainAttributes().getValue("Spring-Boot-Classpath-Index"))
				.isEqualTo("WEB-INF/classpath.idx");
			assertThat(entryLines(jarFile, "WEB-INF/classpath.idx")).containsExactly(
					"- \"WEB-INF/lib/first-library.jar\"", "- \"WEB-INF/lib/second-library.jar\"",
					"- \"WEB-INF/lib/third-library-SNAPSHOT.jar\"", "- \"WEB-INF/lib/fourth-library.jar\"",
					"- \"WEB-INF/lib/first-project-library.jar\"",
					"- \"WEB-INF/lib/second-project-library-SNAPSHOT.jar\"");
		}
	}

	@Test
	void javaVersionIsWrittenToManifest() throws IOException {
		try (JarFile jarFile = new JarFile(createPopulatedJar())) {
			assertThat(jarFile.getManifest().getMainAttributes().getValue("Build-Jdk-Spec"))
				.isEqualTo(JavaVersion.VERSION_17.getMajorVersion());
		}
	}

	@Override
	protected void executeTask() {
		getTask().copy();
	}

	@Override
	void applyLayered(Action<LayeredSpec> action) {
		getTask().layered(action);
	}

}
