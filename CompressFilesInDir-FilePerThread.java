import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import java.util.concurrent.ExecutorService;

/*
	A program that browses a given list of folders and zips all the regular files
	contained in each folder (it is NOT recursive in sub-folders, for semplicity).
	
	In this version, the task given to the thread is considered to be a single file.
*/
//	Program takes a list of absolute paths of folders in input.

public class CompressFilesInDir implements Runnable {
	private String source, target;

	public CompressFilesInDir(String source, String target) {
		this.source = source;
		this.target = target;
	}

	public void compressGzip(String source, String target) {
		try {
			GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(target));
			FileInputStream fis = new FileInputStream(source);
			int len = 0;
			byte[] buff = new byte[2048];
			while ((len = fis.read(buff)) > 0) {
				gos.write(buff, 0, len);
			}
			fis.close();
			gos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		compressGzip(source, target);
		System.out.printf("%s: compressed %s\n", Thread.currentThread().getName(), source);
	}

	public static void main(String[] args) {
		ExecutorService threadPool = Executors.newFixedThreadPool(4);
		for (int i = 0; i < args.length; i++) {
			File directory = new File(args[i]);
			File[] contents = directory.listFiles();
			for (File f : contents) {
				if (f.isDirectory()) {
					continue;
				} else {
					String source = f.getAbsolutePath();
					String target = source + ".gz";
					threadPool.execute(new CompressFilesInDir(source, target));
				}
			}
		}
		threadPool.shutdown();
	}
}