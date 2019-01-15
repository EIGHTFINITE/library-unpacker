package nice.work.forge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import org.tukaani.xz.XZInputStream;
import com.google.common.io.Files;

public class LibraryUnpacker {
	private static boolean createChecksums = false;

	public static void main(final String[] args) throws IOException {
		if (args.length < 1) {
			System.out.println("Usage: java -cp library-unpacker.jar nice.work.forge.LibraryUnpacker [-options] <file1> [file2...]");
			System.out.println("where options include:");
			System.out.println("    --checksums   write checksums to jar");
			return;
		}
		for (final String arg : args) {
			if (arg.startsWith("--")) {
				if (arg.contains("checksums"))
					createChecksums = true;
				continue;
			}
			final File packFile = new File(arg);
			String libPathName = packFile.getName();
			if (libPathName.endsWith(".pack.xz")) {
				libPathName = libPathName.substring(0, arg.length() - 8);
			}
			final File libPath = new File(packFile.getParentFile(), libPathName);
			unpackLibrary(libPath, Files.toByteArray(packFile));
		}
	}

	private static void unpackLibrary(File output, byte[] data) throws IOException {
		if (output.exists()) {
			output.delete();
		}

		byte[] decompressed = readFully(new XZInputStream(new ByteArrayInputStream(data)));

		// Snag the checksum signature
		String end = new String(decompressed, decompressed.length - 4, 4);
		if (!end.equals("SIGN")) {
			System.out.println("Unpacking failed, signature missing " + end);
			return;
		}

		int x = decompressed.length;
		int len =
				((decompressed[x - 8] & 0xFF)      ) |
				((decompressed[x - 7] & 0xFF) << 8 ) |
				((decompressed[x - 6] & 0xFF) << 16) |
				((decompressed[x - 5] & 0xFF) << 24);

		File temp = File.createTempFile("art", ".pack");
		System.out.println("  Signed");
		System.out.println("  Checksum Length: " + len);
		System.out.println("  Total Length:    " + (decompressed.length - len - 8));
		System.out.println("  Temp File:       " + temp.getAbsolutePath());

		byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

		// As Pack200 copies all the data from the input, this creates duplicate data in memory.
		// Which on some systems triggers a OutOfMemoryError, to counter this, we write the data
		// to a temporary file, force GC to run {I know, eww} and then unpack.
		// This is a tradeoff of disk IO for memory.
		// Should help mac users who have a lower standard max memory then the rest of the world (-.-)
		OutputStream out = new FileOutputStream(temp);
		out.write(decompressed, 0, decompressed.length - len - 8);
		out.close();
		decompressed = null;
		data = null;
		System.gc();

		FileOutputStream jarBytes = new FileOutputStream(output);
		JarOutputStream jos = new JarOutputStream(jarBytes);

		Pack200.newUnpacker().unpack(temp, jos);

		if(createChecksums) {
			JarEntry checksumsFile = new JarEntry("checksums.sha1");
			checksumsFile.setTime(0);
			jos.putNextEntry(checksumsFile);
			jos.write(checksums);
			jos.closeEntry();
		}

		jos.close();
		jarBytes.close();
		temp.delete();
	}

	private static byte[] readFully(final InputStream stream) throws IOException {
		final byte[] data = new byte[4096];
		final ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
		int len;
		do {
			len = stream.read(data);
			if (len > 0) {
				entryBuffer.write(data, 0, len);
			}
		} while (len != -1);
		return entryBuffer.toByteArray();
	}
}
