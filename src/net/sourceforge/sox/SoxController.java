package net.sourceforge.sox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.MediaDesc;
import org.ffmpeg.android.ShellUtils.ShellCallback;

import android.content.Context;
import android.util.Log;

public class SoxController {
	private final static String TAG = "SOX";
	String[] libraryAssets = {"sox"};
	private String soxBin;
	private File fileBinDir;
	private Context context;
	private ShellCallback callback;

	private NumberFormat mFormatValue;
	private DecimalFormat mFormatTrim;

	public SoxController(Context _context, ShellCallback _callback) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);
		callback = _callback;

		if (!new File(fileBinDir, libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}

		soxBin = new File(fileBinDir,"sox").getCanonicalPath();

		mFormatValue = NumberFormat.getInstance(Locale.US);
		mFormatValue.setMaximumFractionDigits(1);

        mFormatTrim = (DecimalFormat)DecimalFormat.getInstance(Locale.US);
        mFormatTrim.applyPattern("###.####");
	}

	
	private class LengthParser implements ShellCallback {
		public double length;
		public int retValue = -1;

		@Override
		public void shellOut(String shellLine) {
			Log.d("sox", shellLine);
			if( !shellLine.startsWith("Length") )
				return;
			String[] split = shellLine.split(":");
			if(split.length != 2) return;

			String lengthStr = split[1].trim();

			try {
				length = Double.parseDouble( lengthStr );
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void processComplete(int exitValue) {
			retValue = exitValue;

		}
	}
	
	/**
	 * Retrieve the length of the audio file
	 * sox file.wav 2>&1 -n stat | grep Length | cut -d : -f 2 | cut -f 1
	 * @return the length in seconds or null
	 */
	public double getLength(String path) {
		ArrayList<String> cmd = new ArrayList<String>();

		cmd.add(soxBin);
		cmd.add(path);
		cmd.add("-n");
		cmd.add("stat");

		LengthParser sc = new LengthParser();

		try {
			execSox(cmd, sc);
		} catch (IOException e) {
			Log.e(TAG,"error getting length ",e);
		} catch (InterruptedException e) {
			Log.e(TAG,"error getting length",e);
		}

		return sc.length;
	}


	/**
	 * Change audio volume
	 * sox -v volume <path> outFile
	 * @param volume
	 * @return path to trimmed audio
	 */
	public String setVolume(String inputFile, float volume, String outputFile) throws IOException {

		ArrayList<String> cmd = new ArrayList<String>();

		File file = new File(inputFile);
		cmd.add(soxBin);
		cmd.add("-v");
		cmd.add(mFormatValue.format(volume));
		cmd.add(inputFile);
		cmd.add(outputFile);

		try {
			int rc = execSox(cmd, callback);
			if( rc != 0 ) {
				Log.e(TAG, "trimAudio receieved non-zero return code!");
				return null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (file.exists())
			return outputFile;
		else
			return null;

	}

    /**
     * Discard all audio not between start and length (length = end by default)
     * sox <path> -e signed-integer -b 16 outFile trim <start> <length>
     * @param start
     * @param length (optional)
     * @return path to trimmed audio
     */
    public String trimAudio(String path, double start, double length, float volume) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();

		File file = new File(path);
        String outFile = file.getCanonicalPath() + "_trimmed.wav";
        cmd.add(soxBin);
		cmd.add("-v");
		cmd.add(mFormatValue.format(volume));
        cmd.add(path);
        cmd.add("-e");
        cmd.add("signed-integer");
        cmd.add("-b");
        cmd.add("16");
        cmd.add(outFile);
        cmd.add("trim");
        cmd.add(mFormatTrim.format(start));
        if( length != -1 )
            cmd.add(mFormatTrim.format(length));

        try {
            int rc = execSox(cmd, callback);
            if( rc != 0 ) {
                Log.e(TAG, "trimAudio receieved non-zero return code!");
                outFile = null;
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (file.exists())
            return outFile;
        else
            return null;

    }

    /**
     * Delay audio to start  and trim it at length (length = end by default)
     * sox <path> -e signed-integer -b 16 outFile delay start start trim <start> <length>
     * @param start
     * @param length (optional)
     * @return path to trimmed audio
     */
    public String delayAudio(String path, double start, double length) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();

        // a negative start value means we need to add that amount of delay before the sample
        double startDelay = 0;
        if (start < 0) {
            startDelay = Math.abs(start);
        }
        File file = new File(path);
        String outFile = file.getCanonicalPath() + "_delayed.wav";
        cmd.add(soxBin);
        cmd.add(path);
        cmd.add("-e");
        cmd.add("signed-integer");
        cmd.add("-b");
        cmd.add("16");
        cmd.add(outFile);
        cmd.add("delay");
        cmd.add(mFormatTrim.format(startDelay)); // left channel
        cmd.add(mFormatTrim.format(startDelay)); // right channel
//        cmd.add("trim"); // FIXME how does trim interact with delay?
//        cmd.add(formatDouble(0));
//        if( length != -1 )
//            cmd.add(formatDouble(length));

        try {
            int rc = execSox(cmd, callback);
            if( rc != 0 ) {
                Log.e(TAG, "delayAudio receieved non-zero return code!");
                outFile = null;
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (file.exists())
            return outFile;
        else
            return null;

    }

	/**
	 * Fade audio file
	 * sox <path> outFile fade <type> <fadeInLength> <stopTime> <fadeOutLength>
	 * @param path
	 * @param type
	 * @param fadeInLength specify 0 if no fade in is desired
	 * @param stopTime (optional)
	 * @param fadeOutLength (optional)
	 * @return
	 */
	public String fadeAudio(String path, String type, double fadeInLength, double stopTime, double fadeOutLength ) throws IOException {

		final List<String> curves = Arrays.asList( new String[]{ "q", "h", "t", "l", "p"} );

		if(!curves.contains(type)) {
			Log.e(TAG, "fadeAudio: passed invalid type: " + type);
			return null;
		}

		File file = new File(path);
		String outFile = file.getCanonicalPath() + "_faded.wav";

		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);
		cmd.add(path);
		cmd.add(outFile);
		cmd.add("fade");
		cmd.add(type);
		cmd.add(mFormatTrim.format(fadeInLength));
		if(stopTime != -1)
			cmd.add(mFormatTrim.format(stopTime));
		if(fadeOutLength != -1)
			cmd.add(mFormatTrim.format(fadeOutLength));

		try {
			int rc = execSox(cmd, callback);
			if(rc != 0) {
				Log.e(TAG, "fadeAudio receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Combine and mix audio files
	 * sox -m -v 1.0 file[0] -v 1.0 file[1] ... -v 1.0 file[n] outFile
	 * TODO support passing of volume
	 * @param files
	 * @return combined and mixed file (null on failure)
	 */
	public MediaDesc combineMix(List<MediaDesc> files, MediaDesc outFile) {
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);
		cmd.add("-m");

		for(MediaDesc file : files) {
			cmd.add("-v");
			cmd.add(mFormatValue.format(file.audioVolume));
			cmd.add(file.path);
		}

		cmd.add(outFile.path);

		try {
			int rc = execSox(cmd, callback);
			if(rc != 0) {
				Log.e(TAG, "combineMix receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Simple combiner
	 * sox file[0] file[1] ... file[n] <outFile>
	 * @param files
	 * @param outFile
	 * @return outFile or null on failure
	 */
	public MediaDesc combine(List<MediaDesc> files, MediaDesc outFile) {
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);

		for(MediaDesc file : files) {
			cmd.add("-v");
			cmd.add(mFormatValue.format(file.audioVolume));
			cmd.add(file.path);
		}
		
		cmd.add(outFile.path);

		try {
			int rc = execSox(cmd, callback);
			if(rc != 0) {
				Log.e(TAG, "combine receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Takes a seconds.frac value and formats it into:
	 * 	hh:mm:ss:ss.frac
	 * @param seconds
	 */
	public String formatTimePeriod(double seconds) {
	
		long milliTime = (long)(seconds * 100f);
		Date dateTime = new Date(milliTime);
		return String.format(Locale.US, "%s:%s.%s", dateTime.getHours(),dateTime.getMinutes(),dateTime.getSeconds());
	}

	public int execSox(List<String> cmd, ShellCallback sc) throws IOException,
			InterruptedException {

		String soxBin = new File(fileBinDir, "sox").getCanonicalPath();
		Runtime.getRuntime().exec("chmod 700 " + soxBin);
		return execProcess(cmd, sc);
	}

	private int execProcess(List<String> cmds, ShellCallback sc)
			throws IOException, InterruptedException {

		//ensure that the arguments are in the correct Locale format
		for (String cmd :cmds)
		{
			cmd = String.format(Locale.US, "%s", cmd);
		}
		
		ProcessBuilder pb = new ProcessBuilder(cmds);
		pb.directory(fileBinDir);

		StringBuffer cmdlog = new StringBuffer();

		for (String cmd : cmds) {
			cmdlog.append(cmd);
			cmdlog.append(' ');
		}

		Log.v(TAG, cmdlog.toString());

		// pb.redirectErrorStream(true);
		Process process = pb.start();

		// any error message?
		StreamGobbler errorGobbler = new StreamGobbler(
				process.getErrorStream(), "ERROR", sc);

		// any output?
		StreamGobbler outputGobbler = new StreamGobbler(
				process.getInputStream(), "OUTPUT", sc);

		// kick them off
		errorGobbler.start();
		outputGobbler.start();

		int exitVal = process.waitFor();

		while (outputGobbler.isAlive() || errorGobbler.isAlive());
		
		sc.processComplete(exitVal);

		return exitVal;
	}

	class StreamGobbler extends Thread {
		InputStream is;
		String type;
		ShellCallback sc;

		StreamGobbler(InputStream is, String type, ShellCallback sc) {
			this.is = is;
			this.type = type;
			this.sc = sc;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					if (sc != null)
						sc.shellOut(line);

			} catch (IOException ioe) {
				Log.e(TAG, "error reading shell slog", ioe);
			}
		}
	}


}
