package dev.noctud.latte.version;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * That the service answers the same question without asking the filesystem again, and that it
 * stops doing so the moment the answer could have changed.
 *
 * Both halves matter and they pull against each other. Remembering nothing makes every tag in a
 * template read a composer.lock; remembering too long makes the plugin answer according to a Latte
 * the project no longer has, and nothing about that looks wrong from the outside.
 *
 * Everything written here is invented. A composer.lock from a real project never enters this
 * repository, not even as a fragment.
 */
public class LatteVersionServiceTest extends BasePlatformTestCase {

	private static final String LOCK_2_11 = """
		{
		    "packages": [
		        { "name": "latte/latte", "version": "v2.11.7" }
		    ]
		}
		""";

	private static final String LOCK_3_1 = """
		{
		    "packages": [
		        { "name": "latte/latte", "version": "v3.1.6" }
		    ]
		}
		""";

	private File root;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		root = FileUtil.createTempDirectory("latte-version-service", null);
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			LatteSettings.getInstance(getProject()).latteVersionOverride = "";
			FileUtil.delete(root);
		} finally {
			super.tearDown();
		}
	}

	/**
	 * The reason the cache exists. Resolving reads whole Composer files, and the registry asks once
	 * per tag - so the second question has to be answered from memory or annotating a template
	 * becomes a sequence of file reads.
	 */
	@Test
	public void testTheSameQuestionIsNotPutToTheFilesystemTwice() throws Exception {
		VirtualFile directory = directoryFor(root);
		CountingReader reader = new CountingReader(LOCK_3_1);
		LatteVersionService service = serviceWith(reader);

		LatteVersion first = service.getVersion(directory);
		LatteVersion second = service.getVersion(directory);

		assertEquals(first, second);
		assertEquals("the answer has to come from memory the second time", 1, reader.reads);
	}

	/**
	 * The other half. A cache that outlives what it was computed from is worse than no cache: the
	 * plugin would keep answering according to a Latte that composer.lock no longer names, and
	 * a wrong answer here reads exactly like a right one.
	 */
	@Test
	public void testAComposerFileChangingOnDiskThrowsTheAnswerAway() throws Exception {
		VirtualFile directory = directoryFor(root);
		VirtualFile lock = WriteAction.computeAndWait(() -> directory.createChildData(this, "composer.lock"));
		WriteAction.runAndWait(() -> VfsUtil.saveText(lock, LOCK_2_11));
		LatteVersionService service = serviceWith(new VirtualFileComposerReader());

		assertEquals("2.11", service.getVersion(directory).line());

		WriteAction.runAndWait(() -> VfsUtil.saveText(lock, LOCK_3_1));

		assertEquals("3.1", service.getVersion(directory).line());
	}

	/**
	 * The forced line is part of what is asked, not something to watch for: a changed override
	 * simply does not find the entry it would have used.
	 */
	@Test
	public void testAChangedForcedLineIsAnsweredAsTheNewOne() throws Exception {
		VirtualFile directory = directoryFor(root);
		LatteVersionService service = serviceWith(new CountingReader(LOCK_3_1));

		assertEquals("3.1", service.getVersion(directory).line());

		LatteSettings.getInstance(getProject()).latteVersionOverride = "2.11";

		LatteVersion forced = service.getVersion(directory);
		assertEquals("2.11", forced.line());
		assertEquals(LatteVersionSource.OVERRIDE, forced.getSource());
	}

	/**
	 * A monorepo with one Latte 2 package and one Latte 3 package is the case the walk starts at
	 * the file for. A cache keyed on anything less than the directory would collapse the two and
	 * hand one package the other's answer.
	 */
	@Test
	public void testTwoDirectoriesKeepTheirOwnAnswers() throws Exception {
		VirtualFile older = packageWith("legacy", LOCK_2_11);
		VirtualFile newer = packageWith("current", LOCK_3_1);
		LatteVersionService service = serviceWith(new VirtualFileComposerReader());

		assertEquals("2.11", service.getVersion(older).line());
		assertEquals("3.1", service.getVersion(newer).line());
		assertEquals("2.11", service.getVersion(older).line());
	}

	private @NotNull VirtualFile packageWith(@NotNull String name, @NotNull String lock) throws Exception {
		File directory = new File(root, name);
		assertTrue(directory.mkdirs());
		VirtualFile virtual = directoryFor(directory);
		VirtualFile file = WriteAction.computeAndWait(() -> virtual.createChildData(this, "composer.lock"));
		WriteAction.runAndWait(() -> VfsUtil.saveText(file, lock));
		return virtual;
	}

	private @NotNull VirtualFile directoryFor(@NotNull File directory) {
		VirtualFile found = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
		assertNotNull(found);
		return found;
	}

	private @NotNull LatteVersionService serviceWith(@NotNull LatteVersionResolver.ComposerFileReader reader) {
		LatteVersionService service = new LatteVersionService(getProject());
		service.readThrough(reader);
		Disposer.register(getTestRootDisposable(), service);
		return service;
	}

	/** Counts what reaches the filesystem, which is the thing the cache is supposed to prevent. */
	private static final class CountingReader implements LatteVersionResolver.ComposerFileReader {

		private final String lock;

		private int reads;

		private CountingReader(@NotNull String lock) {
			this.lock = lock;
		}

		@Override
		public @Nullable String read(@NotNull String directory, @NotNull String fileName) {
			reads++;
			return "composer.lock".equals(fileName) ? lock : null;
		}

		@Override
		public @NotNull List<String> ancestorsOf(@NotNull String directory) {
			return List.of(directory);
		}
	}
}
